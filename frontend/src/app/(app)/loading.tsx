import { Skeleton } from "@/shared/ui/skeleton";

export default function AppLoading() {
  return (
    <div className="flex flex-col gap-4 pt-4" aria-busy>
      <Skeleton className="h-3 w-32" />
      <Skeleton className="h-10 w-80 max-w-full" />
      <Skeleton className="h-4 w-96 max-w-full" />
      <div className="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        <Skeleton variant="card" />
        <Skeleton variant="card" />
        <Skeleton variant="card" />
        <Skeleton variant="card" />
      </div>
    </div>
  );
}
