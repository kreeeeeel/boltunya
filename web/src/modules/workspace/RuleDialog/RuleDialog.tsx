import {Xmark} from '@gravity-ui/icons';
import {Box, Button, Dialog, Flex, Icon, Select, Text} from '@gravity-ui/uikit';
import {useTranslations} from 'next-intl';
import {useState} from 'react';
import type {RuleDialogProps} from '@/modules/workspace/RuleDialog/RuleDialog.types';
import {errorMessage} from '@/shared/api/client';
import {SaveDialogFooter} from '@/shared/ui/SaveDialogFooter/SaveDialogFooter';
import {useSaveFeedback} from '@/shared/ui/SaveDialogFooter/useSaveFeedback';
import {ScrollArea} from '@/shared/ui/ScrollArea/ScrollArea';
import {TelegramAvatar} from '@/shared/ui/TelegramAvatar/TelegramAvatar';
import {normalizeCommunicationRule} from '@/modules/workspace/model/normalizeCommunicationRule';
import {useModelProfiles} from '@/modules/settings/model/useModelProfiles';
import {RuleNavigation} from '@/modules/workspace/RuleDialog/components/RuleNavigation';
import {ruleSections} from '@/modules/workspace/RuleDialog/model/ruleSections';
import type {RuleSectionId} from '@/modules/workspace/RuleDialog/model/ruleSections';
import {RuleReplyFields} from '@/modules/workspace/RuleDialog/sections/RuleReplyFields';
import {RuleToneFields} from '@/modules/workspace/RuleDialog/sections/RuleToneFields';
import {RuleModelFields} from '@/modules/workspace/RuleDialog/sections/RuleModelFields';
import {RuleProactiveFields} from '@/modules/workspace/RuleDialog/sections/RuleProactiveFields';
import {RuleMemoryFields} from '@/modules/workspace/RuleDialog/sections/RuleMemoryFields';
import {RuleMediaFields} from '@/modules/workspace/RuleDialog/sections/RuleMediaFields';
import {RuleGroupFields} from '@/modules/workspace/RuleDialog/sections/RuleGroupFields';
import {RulePreviewFields} from '@/modules/workspace/RuleDialog/sections/RulePreviewFields';
import {ChatVoiceFields} from '@/modules/workspace/RuleDialog/sections/ChatVoiceFields';
import styles from '@/modules/workspace/RuleDialog/RuleDialog.module.css';

export function RuleDialog({
    chat,
    engine,
    customStyles,
    defaultModel,
    defaultModelProfileId,
    settings,
    onClose,
    onCollectContext,
    onDeleted,
    onSave,
}: RuleDialogProps) {
    const t = useTranslations();
    const profiles = useModelProfiles();
    const [rule, setRule] = useState(() =>
        normalizeCommunicationRule(
            chat.rule,
            customStyles.map((style) => style.id),
        ),
    );
    const [section, setSection] = useState<RuleSectionId>('replies');
    const [visited, setVisited] = useState<RuleSectionId[]>(['replies']);
    const [busy, setBusy] = useState('');
    const [error, setError] = useState('');
    const feedback = useSaveFeedback(rule);
    const sections = ruleSections.filter((id) => id !== 'groups' || (chat.kind && chat.kind !== 'private'));
    const profileId = (rule.useDefaultModel ?? true) ? defaultModelProfileId : rule.modelProfileId;
    const previewAvailable = profileId
        ? profiles.profiles.some((profile) => profile.id === profileId && profile.enabled)
        : !!engine?.models.some(
              (model) =>
                  model.id === ((rule.useDefaultModel ?? true) ? defaultModel : rule.model) &&
                  model.installed,
          );

    function select(next: RuleSectionId) {
        setSection(next);
        setVisited((previous) => (previous.includes(next) ? previous : [...previous, next]));
    }

    async function save() {
        setBusy('save');
        setError('');
        feedback.clearSaved();
        try {
            await onSave(rule);
            feedback.markSaved();
        } catch (cause) {
            setError(errorMessage(cause));
        } finally {
            setBusy('');
        }
    }

    function content(id: RuleSectionId) {
        const common = {value: rule, onUpdate: setRule};
        switch (id) {
            case 'replies':
                return <RuleReplyFields {...common} />;
            case 'model':
                return (
                    <RuleModelFields
                        {...common}
                        engine={engine}
                        profiles={profiles.profiles}
                        profileError={profiles.error}
                        defaultModel={defaultModel}
                        defaultModelProfileId={defaultModelProfileId}
                    />
                );
            case 'tone':
                return <RuleToneFields {...common} customStyles={customStyles} />;
            case 'voice':
                return <ChatVoiceFields {...common} settings={settings} />;
            case 'proactive':
                return <RuleProactiveFields {...common} />;
            case 'preview':
                return (
                    <RulePreviewFields
                        rule={rule}
                        chatId={chat.id}
                        available={previewAvailable}
                        disabled={!!busy}
                        onBusyChange={(active) => setBusy(active ? 'preview' : '')}
                    />
                );
            case 'memory':
                return (
                    <RuleMemoryFields
                        {...common}
                        chatId={chat.id}
                        disabled={!!busy}
                        onCollectContext={onCollectContext}
                        onDeleted={onDeleted}
                    />
                );
            case 'media':
                return <RuleMediaFields {...common} />;
            case 'groups':
                return <RuleGroupFields {...common} />;
        }
    }

    return (
        <Dialog
            open
            hasCloseButton={false}
            contentOverflow="visible"
            modalClassName={styles.modal}
            className={styles.dialog}
            qa="communication-dialog"
            onClose={onClose}
            aria-label={t('ruleTitle', {name: chat.title})}
        >
            <Flex className={styles.shell}>
                <Box className={styles.sidebar}>
                    <RuleNavigation chat={chat} selected={section} onSelect={select} />
                </Box>
                <Flex direction="column" className={styles.main}>
                    <Flex direction="column" className={styles.mobileHeading} gap={3}>
                        <Flex alignItems="center" gap={3}>
                            <TelegramAvatar title={chat.title} url={chat.avatarUrl} size="m" />
                            <Text className={styles.chatName}>{chat.title}</Text>
                        </Flex>
                        <Select
                            size="l"
                            width="max"
                            value={[section]}
                            options={sections.map((id) => ({
                                value: id,
                                content: t('chatSettings.sections.' + id),
                            }))}
                            onUpdate={([id]) => {
                                if (sections.some((section) => section === id)) {
                                    select(id as RuleSectionId);
                                }
                            }}
                            aria-label={t('chatSettings.navigation')}
                        />
                    </Flex>
                    <Flex direction="column" gap={2} className={styles.heading}>
                        <Text as="h2" variant="header-1">
                            {t('chatSettings.sections.' + section)}
                        </Text>
                        <Text color="secondary">{t('chatSettings.hints.' + section)}</Text>
                    </Flex>
                    {visited.map((id) => (
                        <Box key={id} hidden={id !== section} className={styles.panel}>
                            <ScrollArea className={styles.scroll} qa={'chat-settings-scroll-' + id}>
                                <Box className={styles.content}>{content(id)}</Box>
                            </ScrollArea>
                        </Box>
                    ))}
                    <Box className={styles.footer} qa="chat-settings-footer">
                        {error && (
                            <Text color="danger" role="alert" className={styles.error}>
                                {error}
                            </Text>
                        )}
                        <SaveDialogFooter
                            saved={feedback.saved}
                            saveLabel={t('save')}
                            cancelLabel={t('cancel')}
                            onClose={onClose}
                            onSave={() => void save()}
                            busy={busy === 'save'}
                            disabled={!!busy}
                        />
                    </Box>
                </Flex>
                <Button
                    className={styles.close}
                    size="m"
                    view="flat-secondary"
                    onClick={onClose}
                    aria-label={t('close')}
                >
                    <Icon data={Xmark} size={18} />
                </Button>
            </Flex>
        </Dialog>
    );
}
