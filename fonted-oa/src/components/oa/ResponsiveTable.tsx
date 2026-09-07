'use client';

import type { ReactNode } from 'react';
import { Card, Empty, Pagination, Spin, Table } from 'antd';
import type { TableProps } from 'antd';
import type { ColumnsType, ColumnType } from 'antd/es/table';
import { useIsMobile } from '@/hooks/useIsMobile';

type AnyRecord = Record<string, any>;

export interface ResponsiveTableProps<T extends AnyRecord> extends TableProps<T> {
  /** 移动端卡片标题，默认取第一列（非操作列）的 render 结果 */
  mobileCardTitle?: (record: T, index: number) => ReactNode;
  /** 移动端卡片底部操作区，默认取 fixed: 'right' 操作列的 render 结果 */
  mobileCardActions?: (record: T, index: number) => ReactNode;
  /** 完全自定义移动端卡片内容（启用后忽略 columns 派生与上面两个配置） */
  mobileCardRender?: (record: T, index: number) => ReactNode;
  /** 需要从移动端字段行中排除的列 key 或 dataIndex */
  mobileHideFieldKeys?: Array<string | number>;
}

function isActionColumn<T extends AnyRecord>(column: ColumnType<T>): boolean {
  return column.fixed === 'right' || column.fixed === true;
}

function columnFieldKey<T extends AnyRecord>(column: ColumnType<T>): string {
  if (column.key != null) return String(column.key);
  if (Array.isArray(column.dataIndex)) return column.dataIndex.join('.');
  return String(column.dataIndex ?? '');
}

function readCellValue<T extends AnyRecord>(record: T, dataIndex: unknown): any {
  if (dataIndex == null) return undefined;
  if (Array.isArray(dataIndex)) {
    return (dataIndex as React.Key[]).reduce<any>(
      (acc, key) => (acc == null ? acc : (acc as AnyRecord)[key as string]),
      record,
    );
  }
  return (record as AnyRecord)[dataIndex as string];
}

/**
 * OA 统一响应式表格：桌面（>720px）渲染原生 antd Table；移动端（≤720px）将同一份
 * columns + dataSource 渲染为卡片列表（首列标题 + 字段行 + 底部操作），主题令牌、
 * 壁纸玻璃材质与空态文案与 Table 保持一致。
 */
export default function ResponsiveTable<T extends AnyRecord>(props: ResponsiveTableProps<T>) {
  const {
    mobileCardTitle,
    mobileCardActions,
    mobileCardRender,
    mobileHideFieldKeys,
    ...tableProps
  } = props;
  const isMobile = useIsMobile();

  if (!isMobile) {
    return <Table<T> {...tableProps} />;
  }

  const {
    columns,
    dataSource,
    rowKey,
    loading,
    locale,
    pagination,
    onRow,
  } = tableProps;

  const records = (dataSource ?? []) as T[];
  const flatColumns = ((columns ?? []) as ColumnsType<T>).filter(
    (column): column is ColumnType<T> => !!column && !('children' in column),
  );
  const hiddenKeys = new Set((mobileHideFieldKeys ?? []).map(String));
  const actionColumn = flatColumns.find(isActionColumn);
  const titleColumn = flatColumns.find((column) => column !== actionColumn);
  const fieldColumns = flatColumns.filter(
    (column) =>
      column !== titleColumn &&
      column !== actionColumn &&
      !hiddenKeys.has(columnFieldKey(column)),
  );

  const resolveRowKey = (record: T, index: number): React.Key => {
    if (typeof rowKey === 'function') return rowKey(record);
    if (typeof rowKey === 'string' || typeof rowKey === 'number') {
      const value = (record as AnyRecord)[rowKey as string];
      if (value != null) return value;
    }
    return index;
  };

  const renderColumn = (
    column: ColumnType<T> | undefined,
    record: T,
    index: number,
  ): ReactNode => {
    if (!column) return null;
    const value = readCellValue(record, column.dataIndex);
    if (column.render) return column.render(value, record, index) as ReactNode;
    if (value == null || value === '') return '-';
    return String(value);
  };

  const isSpinning = typeof loading === 'boolean' ? loading : Boolean((loading as { spinning?: boolean } | undefined)?.spinning);
  const paginationConfig = pagination === false ? null : pagination ?? {};
  const pagerTotal = paginationConfig?.total ?? records.length;
  const pagerPageSize = paginationConfig?.pageSize ?? 10;
  const showPager =
    paginationConfig != null &&
    pagerTotal > 0 &&
    !(paginationConfig.hideOnSinglePage && pagerTotal <= pagerPageSize);

  return (
    <Spin spinning={isSpinning}>
      {records.length === 0 ? (
        <div className="oa-mobile-cards">
          <div className="oa-mobile-empty">
            {locale?.emptyText != null ? (
              (locale.emptyText as any)
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </div>
        </div>
      ) : (
        <div className="oa-mobile-cards">
          {records.map((record, index) => {
            const rowProps = onRow?.(record, index);
            const clickable = typeof rowProps?.onClick === 'function';
            const key = resolveRowKey(record, index);
            return (
              <Card
                key={key}
                size="small"
                variant="borderless"
                className={`oa-mobile-card${clickable ? ' is-clickable' : ''}`}
                onClick={clickable ? (event) => rowProps?.onClick?.(event) : undefined}
              >
                {mobileCardRender ? (
                  mobileCardRender(record, index)
                ) : (
                  <>
                    {mobileCardTitle ? (
                      <div className="oa-mobile-card__title">
                        {mobileCardTitle(record, index)}
                      </div>
                    ) : (
                      titleColumn && (
                        <div className="oa-mobile-card__title">
                          {renderColumn(titleColumn, record, index)}
                        </div>
                      )
                    )}
                    {fieldColumns.length > 0 && (
                      <div className="oa-mobile-card__fields">
                        {fieldColumns.map((column) => (
                          <div
                            className="oa-mobile-card__field"
                            key={columnFieldKey(column) || String(column.title)}
                          >
                            <span className="oa-mobile-card__field-label">
                              {(column.title as any) ?? ''}
                            </span>
                            <span className="oa-mobile-card__field-value">
                              {renderColumn(column, record, index)}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                    {(mobileCardActions ?? actionColumn) && (
                      <div className="oa-mobile-card__actions">
                        {mobileCardActions
                          ? mobileCardActions(record, index)
                          : renderColumn(actionColumn, record, index)}
                      </div>
                    )}
                  </>
                )}
              </Card>
            );
          })}
        </div>
      )}
      {showPager && paginationConfig && (
        <Pagination
          className="oa-mobile-cards__pager"
          simple
          size="small"
          current={paginationConfig.current}
          pageSize={pagerPageSize}
          total={pagerTotal}
          showTotal={paginationConfig.showTotal}
          onChange={paginationConfig.onChange}
          onShowSizeChange={paginationConfig.onShowSizeChange}
        />
      )}
    </Spin>
  );
}
