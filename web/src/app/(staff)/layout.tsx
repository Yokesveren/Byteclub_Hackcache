import { StaffShell } from "@/components/StaffShell";

// Staff pages render after the browser confirms who is signed in, so there is no static shell to
// validate for instant navigation; within the shell, moving between pages is instant anyway.
export const instant = false;

/** Every staff screen shares one shell, mounted once, so moving between pages never reloads it. */
export default function StaffLayout({ children }: LayoutProps<"/">) {
  return <StaffShell>{children}</StaffShell>;
}
