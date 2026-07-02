import { redirect } from "next/navigation";
import { getSession } from "@/lib/session";
import { getUserById, toPublicUser } from "@/lib/users";
import { getShelfForUser } from "@/lib/shelf";
import SettingsForm from "@/components/SettingsForm";
import ShelfManager from "@/components/ShelfManager";

export const metadata = { title: "Ajustes · Tidalshelf" };

export default async function SettingsPage() {
  const session = await getSession();
  if (!session.userId) {
    redirect("/login");
  }
  const user = getUserById(session.userId);
  if (!user) {
    redirect("/login");
  }
  const items = getShelfForUser(user.id);

  return (
    <div className="flex flex-col gap-10">
      <div>
        <h1 className="text-2xl font-semibold">Ajustes</h1>
        <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
          Tu perfil público está en{" "}
          <a className="underline" href={`/u/${user.username}`}>
            /u/{user.username}
          </a>
          .
        </p>
      </div>
      <SettingsForm user={toPublicUser(user)} />
      <ShelfManager initialItems={items} />
    </div>
  );
}
