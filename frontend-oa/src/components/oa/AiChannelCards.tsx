import { useRef, useEffect, useState } from 'react';
import { Alert, Button, Card, Space, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { OaIcon } from '@/components/OaIcon';
import { aiConfigurationApi, type AiChannel } from '@/lib/aiConfigurationApi';

interface Props {
 channels: AiChannel[];
 selectedId: number;
 onSelect: (id: number) => void;
 onAdd: () => void;
}

export default function AiChannelCards({ channels, selectedId, onSelect, onAdd }: Props) {
 const { t } = useTranslation();
 const [testing, setTesting] = useState<number>();
 const [result, setResult] = useState<{ id: number; success: boolean; text: string }>();
 const active = useRef(true);
 useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
 const test = async (channel: AiChannel) => {
  setTesting(channel.id); setResult(undefined);
  try {
   await aiConfigurationApi.test({ channelId: channel.id, baseUrl: channel.configuration.baseUrl, model: channel.configuration.model });
   if (active.current) setResult({ id: channel.id, success: true, text: t('aiConfiguration.testPassedFor', { model: channel.configuration.model }) });
  } catch (error) {
   if (active.current) setResult({ id: channel.id, success: false, text: error instanceof Error ? error.message : t('aiConfiguration.saveFailed') });
  } finally { if (active.current) setTesting(undefined); }
 };
 return <section className="oa-channel-section" aria-label={t('aiConfiguration.channelsTitle')}>
  <div className="oa-channel-toolbar">
   <div><Typography.Title level={4}>{t('aiConfiguration.channelsTitle')}</Typography.Title><Typography.Text type="secondary">{t('aiConfiguration.channelsCount', { count: channels.length })}</Typography.Text></div>
   <Button type="primary" icon={<OaIcon name="add" />} onClick={onAdd}>{t('aiConfiguration.addChannel')}</Button>
  </div>
  <div className="oa-channel-grid">
   {channels.map(channel => <Card key={channel.id} className={`oa-domain-card oa-channel-card${selectedId === channel.id ? ' oa-channel-selected' : ''}`}>
    <div className="oa-channel-heading">
     <div className="oa-channel-identity"><span className="oa-channel-avatar"><OaIcon name="ai" /></span><Typography.Text strong ellipsis>{channel.name}</Typography.Text></div>
     <Tag color={channel.id === 1 ? 'green' : undefined}>{t(channel.id === 1 ? 'aiConfiguration.defaultChannel' : 'aiConfiguration.apiChannel')}</Tag>
    </div>
    <Space size={6} wrap className="oa-channel-badges"><Tag>{t('aiConfiguration.protocol')}</Tag><Tag color={channel.configuration.keyConfigured ? 'cyan' : 'orange'}>{t(channel.configuration.keyConfigured ? 'aiConfiguration.keyReady' : 'aiConfiguration.keyMissing')}</Tag></Space>
    <dl className="oa-channel-details">
     <div><dt>{t('aiConfiguration.baseUrl')}</dt><dd>{channel.configuration.baseUrl}</dd></div>
     <div><dt>{t('aiConfiguration.defaultModelLabel')}</dt><dd>{channel.configuration.model}</dd></div>
     <div><dt>{t('aiConfiguration.directory')}</dt><dd>{t('aiConfiguration.enabledCount', { count: channel.configuration.models.length })}</dd></div>
    </dl>
    {result?.id === channel.id && <Alert type={result.success ? 'success' : 'error'} showIcon title={result.text} />}
    <div className="oa-channel-actions">
     <Typography.Text type="secondary">{t('aiConfiguration.channelNumber', { id: channel.id })}</Typography.Text>
     <Space><Button onClick={() => onSelect(channel.id)}>{t('aiConfiguration.configureChannel')}</Button><Button loading={testing === channel.id} disabled={testing !== undefined} onClick={() => void test(channel)}>{t('aiConfiguration.testChannel')}</Button></Space>
    </div>
   </Card>)}
   {!channels.length && <Card className="oa-domain-card oa-channel-card"><Typography.Paragraph type="secondary">{t('aiConfiguration.noChannels')}</Typography.Paragraph><Button onClick={() => onSelect(1)}>{t('aiConfiguration.configureChannel')}</Button></Card>}
  </div>
 </section>;
}
