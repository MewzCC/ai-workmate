import AdminLayout from '@/components/oa/AdminLayout';

interface OaPageProps {
  params: {
    pageId: string;
  };
}

export default function OaDynamicPage({ params }: OaPageProps) {
  return <AdminLayout initialPageId={params.pageId} />;
}
