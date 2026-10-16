import {Box, Flex, Text} from '@gravity-ui/uikit';

import styles from '@/modules/workspace/StyleOption/StyleOption.module.css';

export function StyleOption({emoji, name}: {readonly emoji: string; readonly name: string}) {
    return (
        <Flex className={styles.option} alignItems="center">
            <Box className={styles.emoji} aria-hidden="true" qa="style-emoji">
                {emoji}
            </Box>
            <Text className={styles.name} qa="style-name">
                {name}
            </Text>
        </Flex>
    );
}
