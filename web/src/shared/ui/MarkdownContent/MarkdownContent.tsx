import {Box, Link, Text} from '@gravity-ui/uikit';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

import styles from '@/shared/ui/MarkdownContent/MarkdownContent.module.css';

export function MarkdownContent({children}: {readonly children: string}) {
    return (
        <Box className={styles.root}>
            <Markdown
                remarkPlugins={[remarkGfm]}
                skipHtml
                components={{
                    img: ({alt}) => <Text color="secondary">{alt}</Text>,
                    a: ({href, children: content}) => (
                        <Link href={href ?? ''} target="_blank" rel="noopener noreferrer">
                            {content}
                        </Link>
                    ),
                }}
            >
                {children}
            </Markdown>
        </Box>
    );
}
