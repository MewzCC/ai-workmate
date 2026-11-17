import { useCallback, useEffect, useState, type ComponentProps } from 'react';
import { approvalEngineApi } from '@/lib/approvalEngineApi';
import { BusinessUserSelect } from './BusinessUserSelect';

type Props = Omit<ComponentProps<typeof BusinessUserSelect<string>>, 'query' | 'selectedOption'> & {
  formKey: string;
  fieldName: string;
};

export function ApprovalFormReferenceSelect({ formKey, fieldName, scopeKey, active, value, ...props }: Props) {
  const query = useCallback((params: { keyword?: string; page: number; size: number }) =>
    approvalEngineApi.formReferenceOptions({ formKey, fieldName, ...params }), [formKey, fieldName]);
  const scope = JSON.stringify([scopeKey, formKey, fieldName, value]);
  const [selected, setSelected] = useState<{ scope: string; option: { id: string; name: string } }>();

  useEffect(() => {
    if (!active || !value) return;
    let current = true;
    void approvalEngineApi.formReferenceOptions({ formKey, fieldName, selectedId: value, page: 1, size: 1 })
      .then(({ records }) => {
        const option = records.find(record => record.id === value);
        if (current && option) setSelected({ scope, option });
      })
      .catch(() => { if (current) setSelected(undefined); });
    return () => { current = false; };
  }, [active, fieldName, formKey, scope, value]);

  return <BusinessUserSelect<string> {...props} value={value} active={active} scopeKey={scopeKey} query={query}
    selectedOption={selected?.scope === scope ? selected.option : undefined} />;
}
