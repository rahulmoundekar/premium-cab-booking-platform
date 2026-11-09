import type { Metadata } from "next";
export const metadata: Metadata = { title: "Suabhi Cab Booking", description: "Premium chauffeur-driven travel across Pune and Mumbai." };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) { return <html lang="en"><body>{children}</body></html>; }
