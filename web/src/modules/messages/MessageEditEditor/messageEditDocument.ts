import type {Node as ProseMirrorNode, Schema} from '@gravity-ui/markdown-editor/pm/model';
import type {
    MessageEditDraft,
    MessageEditEntity,
    MessageEditMark,
} from '@/modules/messages/MessageEditEditor/MessageEditEditor.types';

export const entityMarkName = 'telegramEntity';
export const softBreakName = 'telegramSoftBreak';
export const atomicEntityName = 'telegramAtomicEntity';

export function entityAttributes(entity: MessageEditEntity, entityId: string): MessageEditMark {
    return {
        entityId,
        kind: entity.kind,
        sourceIndex: entity.sourceIndex,
        typeHint: entity.typeHint,
        url: entity.url,
        language: entity.language,
    };
}

function invalidDocument(): never {
    throw new Error('MESSAGE_EDIT_INVALID_DOCUMENT');
}

function isBoundary(text: string, position: number): boolean {
    const before = text.charCodeAt(position - 1);
    const after = text.charCodeAt(position);
    return !(before >= 0xd800 && before <= 0xdbff && after >= 0xdc00 && after <= 0xdfff);
}

export function createMessageEditDocument(schema: Schema, draft: MessageEditDraft): ProseMirrorNode {
    const entries = draft.entities.map((entity, index) => ({
        ...entity,
        entityId: `initial-${index}`,
        end: entity.offset + entity.length,
    }));
    if (
        draft.text.includes('\r') ||
        entries.some(
            (entity) =>
                !Number.isInteger(entity.offset) ||
                !Number.isInteger(entity.length) ||
                entity.offset < 0 ||
                entity.length <= 0 ||
                entity.end > draft.text.length ||
                !isBoundary(draft.text, entity.offset) ||
                !isBoundary(draft.text, entity.end),
        )
    ) {
        invalidDocument();
    }
    const atoms = entries.filter(
        (entity) =>
            entity.kind === 'existing' &&
            (entity.typeHint === 'customemoji' || entity.typeHint === 'mentionname'),
    );
    if (
        atoms.some((atom, index) =>
            atoms.some((other, next) => next > index && atom.offset < other.end && other.offset < atom.end),
        )
    ) {
        invalidDocument();
    }
    const marks = entries.filter((entity) => !atoms.includes(entity));
    const newlinePositions = draft.text
        .split('\n')
        .reduce<number[]>((positions, line, index) => {
            if (index > 0) {
                positions.push((positions.at(-1) ?? -1) + line.length + 1);
            } else {
                positions.push(line.length);
            }
            return positions;
        }, [])
        .slice(0, -1);
    const points = [
        ...new Set([
            0,
            draft.text.length,
            ...entries.flatMap((entity) => [entity.offset, entity.end]),
            ...newlinePositions.flatMap((position) => [position, position + 1]),
        ]),
    ].sort((left, right) => left - right);

    function content(from: number, to: number, insideAtom = false): ProseMirrorNode[] {
        const result: ProseMirrorNode[] = [];
        let position = from;
        points
            .filter((point) => point > from && point <= to)
            .forEach((end) => {
                if (end <= position) {
                    return;
                }
                const atom = !insideAtom ? atoms.find((entity) => entity.offset === position) : undefined;
                if (atom) {
                    result.push(
                        schema.nodes[atomicEntityName].create(
                            entityAttributes(atom, atom.entityId),
                            content(atom.offset, atom.end, true),
                        ),
                    );
                    position = atom.end;
                    return;
                }
                const activeMarks = marks
                    .filter((entity) => entity.offset <= position && entity.end >= end)
                    .map((entity) =>
                        schema.marks[entityMarkName].create(entityAttributes(entity, entity.entityId)),
                    );
                const text = draft.text.slice(position, end);
                result.push(
                    text === '\n'
                        ? schema.nodes[softBreakName].create(null, null, activeMarks)
                        : schema.text(text, activeMarks),
                );
                position = end;
            });
        return result;
    }
    return schema.node('doc', null, schema.node('paragraph', null, content(0, draft.text.length)));
}

export function readMessageEditDocument(doc: ProseMirrorNode): MessageEditDraft {
    let text = '';
    const ranges = new Map<string, {attrs: MessageEditMark; ranges: {offset: number; end: number}[]}>();
    function add(attrs: MessageEditMark, offset: number, end: number) {
        if (end <= offset) {
            return;
        }
        const entry = ranges.get(attrs.entityId) ?? {attrs, ranges: []};
        entry.ranges.push({offset, end});
        ranges.set(attrs.entityId, entry);
    }
    function visit(node: ProseMirrorNode) {
        if (node.marks.some((mark) => mark.type.name !== entityMarkName)) {
            invalidDocument();
        }
        const offset = text.length;
        if (node.isText) {
            text += node.text;
        } else if (node.type.name === softBreakName) {
            text += '\n';
        } else if (['doc', 'paragraph', atomicEntityName].includes(node.type.name)) {
            node.forEach(visit);
        } else {
            invalidDocument();
        }
        if (node.type.name === atomicEntityName) {
            add(node.attrs as MessageEditMark, offset, text.length);
        }
        node.marks
            .filter((mark) => mark.type.name === entityMarkName)
            .forEach((mark) => {
                add(mark.attrs as MessageEditMark, offset, text.length);
            });
    }
    visit(doc);
    const entities: MessageEditEntity[] = [];
    ranges.forEach(({attrs, ranges: fragments}) => {
        const merged: {offset: number; end: number}[] = [];
        fragments
            .sort((left, right) => left.offset - right.offset)
            .forEach((fragment) => {
                const previous = merged.at(-1);
                if (previous && fragment.offset <= previous.end) {
                    previous.end = Math.max(previous.end, fragment.end);
                } else {
                    merged.push({...fragment});
                }
            });
        merged.forEach(({offset, end}) => {
            entities.push({
                kind: attrs.kind,
                sourceIndex: attrs.sourceIndex,
                typeHint: attrs.typeHint,
                url: attrs.url,
                language: attrs.language,
                offset,
                length: end - offset,
            });
        });
    });
    entities.sort(
        (left, right) =>
            left.offset - right.offset ||
            right.length - left.length ||
            (left.sourceIndex ?? -1) - (right.sourceIndex ?? -1),
    );
    return {text, entities};
}
