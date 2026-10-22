import {Magnifier} from '@gravity-ui/icons';
import {Box, Icon, TextInput} from '@gravity-ui/uikit';
import type {SelectRenderFilter} from '@gravity-ui/uikit';
import styles from '@/modules/workspace/RuleDialog/RuleDialog.module.css';

export function RuleSelectFilter({inputProps, ref}: Parameters<SelectRenderFilter>[0]) {
    return (
        <Box className={styles.selectFilter}>
            <TextInput
                {...inputProps}
                size="l"
                controlRef={ref}
                controlProps={inputProps}
                startContent={
                    <Box className={styles.searchIcon}>
                        <Icon data={Magnifier} size={18} />
                    </Box>
                }
            />
        </Box>
    );
}
