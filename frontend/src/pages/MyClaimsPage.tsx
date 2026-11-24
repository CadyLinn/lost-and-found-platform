import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api } from '../api/endpoints'
import { ClaimStatusBadge, ErrorBox, Spinner, TypeBadge } from '../components/ui'
import { formatDateTime } from '../lib/labels'

export function MyClaimsPage() {
  const claims = useQuery({ queryKey: ['my-claims'], queryFn: api.myClaims })

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="mb-6 text-2xl font-bold text-slate-900">我的認領</h1>

      {claims.isPending ? (
        <Spinner />
      ) : claims.isError ? (
        <ErrorBox message={claims.error.message} />
      ) : claims.data.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 bg-white py-16 text-center text-sm text-slate-500">
          你還沒有回應過任何貼文
        </div>
      ) : (
        <ul className="space-y-3">
          {claims.data.map((claim) => (
            <li key={claim.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs">
              <div className="flex flex-wrap items-center gap-2">
                <TypeBadge type={claim.itemType} />
                <Link to={`/items/${claim.itemId}`} className="font-medium text-slate-900 hover:text-teal-800">
                  {claim.itemTitle}
                </Link>
                <span className="ml-auto">
                  <ClaimStatusBadge status={claim.status} />
                </span>
              </div>
              <p className="mt-2 line-clamp-2 text-sm text-slate-600">{claim.message}</p>
              <p className="mt-2 text-xs text-slate-500">{formatDateTime(claim.createdAt)}</p>
              {claim.contactEmail && (
                <p className="mt-3 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
                  發文者的 Email：
                  <a href={`mailto:${claim.contactEmail}`} className="font-medium underline">
                    {claim.contactEmail}
                  </a>
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
