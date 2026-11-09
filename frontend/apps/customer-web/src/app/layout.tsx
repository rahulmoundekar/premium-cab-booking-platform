import type { Metadata } from "next";
import type { ReactNode } from "react";
import "./globals.css";

export const metadata: Metadata = {
  title: "Suabhi Cab Booking | Premium Private Travel",
  description: "Premium chauffeur-driven airport transfers, local rentals and outstation journeys across Pune and Mumbai.",
};

export default function RootLayout({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
