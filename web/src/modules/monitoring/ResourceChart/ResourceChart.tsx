'use client';

import {Box, Flex, Text} from '@gravity-ui/uikit';
import {Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts';
import {useLocale} from 'next-intl';
import styles from '@/modules/monitoring/ResourceChart/ResourceChart.module.css';

export function ResourceChart({
    title,
    value,
    points,
    lines,
    maximum,
}: {
    readonly title: string;
    readonly value: string;
    readonly points: Record<string, number | null>[];
    readonly lines: readonly {key: string; label: string; color: string}[];
    readonly maximum?: number;
}) {
    const locale = useLocale();
    return (
        <Box className={styles.card}>
            <Flex justifyContent="space-between" alignItems="center" gap={4}>
                <Text variant="subheader-2">{title}</Text>
                <Text className={styles.value}>{value}</Text>
            </Flex>
            <Box className={styles.chart} role="img" aria-label={title}>
                <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={points} margin={{top: 12, right: 6, bottom: 0, left: -20}}>
                        <CartesianGrid stroke="var(--tiho-line)" vertical={false} />
                        <XAxis
                            dataKey="timestamp"
                            tickFormatter={(timestamp) =>
                                new Date(timestamp).toLocaleTimeString(locale, {
                                    hour: '2-digit',
                                    minute: '2-digit',
                                    second: '2-digit',
                                })
                            }
                            minTickGap={60}
                            tick={{fontSize: 12, fill: 'var(--tiho-muted)'}}
                            axisLine={false}
                            tickLine={false}
                        />
                        <YAxis
                            domain={[0, maximum ?? 'auto']}
                            tick={{fontSize: 12, fill: 'var(--tiho-muted)'}}
                            axisLine={false}
                            tickLine={false}
                        />
                        <Tooltip
                            contentStyle={{
                                background: 'var(--tiho-panel)',
                                border: 'none',
                                borderRadius: 10,
                                color: 'var(--tiho-ink)',
                            }}
                            labelFormatter={(label) => new Date(Number(label)).toLocaleTimeString(locale)}
                        />
                        {lines.map((line) => (
                            <Area
                                key={line.key}
                                type="monotone"
                                dataKey={line.key}
                                name={line.label}
                                stroke={line.color}
                                fill={line.color}
                                fillOpacity={0.1}
                                strokeWidth={2}
                                isAnimationActive={false}
                                connectNulls={false}
                            />
                        ))}
                    </AreaChart>
                </ResponsiveContainer>
            </Box>
            <Flex gap={4} className={styles.legend}>
                {lines.map((line) => (
                    <Text key={line.key} style={{color: line.color}}>
                        {line.label}
                    </Text>
                ))}
            </Flex>
        </Box>
    );
}
