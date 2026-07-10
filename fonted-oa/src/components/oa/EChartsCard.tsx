'use client';

import { useEffect, useRef } from 'react';
import { Card } from 'antd';
import * as echarts from 'echarts';
import type { EChartsOption } from 'echarts';

interface EChartsCardProps {
  title: string;
  option: EChartsOption;
  height?: number;
}

export default function EChartsCard({ title, option, height = 280 }: EChartsCardProps) {
  const chartRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!chartRef.current) return;

    const chart = echarts.init(chartRef.current);
    chart.setOption(option);

    const resize = () => chart.resize();
    window.addEventListener('resize', resize);

    return () => {
      window.removeEventListener('resize', resize);
      chart.dispose();
    };
  }, [option]);

  return (
    <Card title={title} className="oa-card oa-chart-card">
      <div ref={chartRef} style={{ height }} />
    </Card>
  );
}
