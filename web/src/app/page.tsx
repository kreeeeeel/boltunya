import {Workspace} from '@/modules/workspace/Workspace/Workspace';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';

export default function Page() {
    return (
        <ScrollArea fillViewport qa="app-scroll">
            <Workspace />
        </ScrollArea>
    );
}
