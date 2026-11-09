import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Suabhi Cab Booking",
  description: "Premium cab booking for airport, local, outstation and group journeys."
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body>{children}</body></html>;
}
