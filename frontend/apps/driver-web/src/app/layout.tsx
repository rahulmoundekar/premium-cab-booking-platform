import type { Metadata } from "next";
export const metadata: Metadata = { title: "Suabhi Driver", description: "Suabhi driver workspace." };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) { return <html lang="en"><body style={{margin:0,fontFamily:"Arial,sans-serif",background:"#101820",color:"#f4f6f7"}}>{children}</body></html>; }
