import {CircleStop, TriangleExclamation} from '@gravity-ui/icons';
import {Alert, Box, Flex, Icon, Text} from '@gravity-ui/uikit';
import type {StatusNoticeProps} from '@/shared/ui/StatusNotice/StatusNotice.types';
import styles from '@/shared/ui/StatusNotice/StatusNotice.module.css';

export function StatusNotice({
    title,
    description,
    variant = 'error',
    actions,
    className,
    onClose,
}: StatusNoticeProps) {
    return (
        <Box className={className} role={variant === 'error' ? 'alert' : 'status'} aria-label={title}>
            <Alert
                className={styles.root}
                theme={variant === 'error' ? 'danger' : 'normal'}
                icon={null}
                title={
                    <Flex alignItems="center" gap={2}>
                        <Icon data={variant === 'error' ? TriangleExclamation : CircleStop} size={18} />
                        <Text variant="subheader-2">{title}</Text>
                    </Flex>
                }
                message={description}
                actions={actions}
                onClose={onClose}
            />
        </Box>
    );
}
