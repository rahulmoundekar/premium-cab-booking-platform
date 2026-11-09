import {
  Home as HomeIcon,
  EventNote as BookingIcon,
  AltRoute as DispatchIcon,
  DirectionsCar as FleetIcon,
  Groups as DriversIcon,
  People as CustomersIcon,
  PriceChange as PricingIcon,
  Payments as PaymentsIcon,
  Business as CorporateIcon,
  Map as MapIcon,
  Assessment as ReportsIcon,
  Settings as SettingsIcon,
  AccountCircle as ProfileIcon,
} from '@mui/icons-material';

const structure = [
  { id: 1, label: 'Operations Dashboard', link: '/app/dashboard', icon: <HomeIcon /> },
  { id: 2, label: 'Bookings', link: '/app/bookings/upcoming', icon: <BookingIcon />, children: [
    { label: 'All Bookings', link: '/app/bookings/all' },
    { label: 'Upcoming', link: '/app/bookings/upcoming' },
    { label: 'In Progress', link: '/app/bookings/in-progress' },
    { label: 'Completed', link: '/app/bookings/completed' },
    { label: 'Cancellations', link: '/app/bookings/cancellations' },
  ]},
  { id: 3, label: 'Dispatch & Trips', link: '/app/dispatch/board', icon: <DispatchIcon />, children: [
    { label: 'Dispatch Board', link: '/app/dispatch/board' },
    { label: 'Trip Monitor', link: '/app/dispatch/trips' },
  ]},
  { id: 4, label: 'Fleet Management', link: '/app/fleet/vehicles', icon: <FleetIcon />, children: [
    { label: 'Vehicle Inventory', link: '/app/fleet/vehicles' },
    { label: 'Vehicle Categories', link: '/app/fleet/categories' },
    { label: 'Vehicle Models', link: '/app/fleet/models' },
    { label: 'Documents & Compliance', link: '/app/fleet/documents' },
    { label: 'Maintenance', link: '/app/fleet/maintenance' },
  ]},
  { id: 5, label: 'Drivers', link: '/app/drivers', icon: <DriversIcon />, children: [
    { label: 'Driver Directory', link: '/app/drivers' },
    { label: 'Onboarding & Verification', link: '/app/drivers/onboarding' },
    { label: 'Driver Shifts', link: '/app/drivers/shifts' },
  ]},
  { id: 6, label: 'Customers', link: '/app/customers', icon: <CustomersIcon /> },
  { id: 7, label: 'Pricing & Rate Plans', link: '/app/pricing', icon: <PricingIcon />, children: [
    { label: 'Fare Plans', link: '/app/pricing/fare-plans' },
    { label: 'Surcharges & Allowances', link: '/app/pricing/extras' },
    { label: 'Coupons', link: '/app/pricing/coupons' },
  ]},
  { id: 8, label: 'Payments & Refunds', link: '/app/payments', icon: <PaymentsIcon />, children: [
    { label: 'Transactions', link: '/app/payments/transactions' },
    { label: 'Refunds', link: '/app/payments/refunds' },
    { label: 'Settlement Reconciliation', link: '/app/payments/settlements' },
  ]},
  { id: 9, label: 'Corporate Accounts', link: '/app/corporate', icon: <CorporateIcon /> },
  { id: 10, label: 'Service Areas', link: '/app/service-areas', icon: <MapIcon /> },
  { id: 11, label: 'Reports & Analytics', link: '/app/reports', icon: <ReportsIcon />, children: [
    { label: 'Booking Reports', link: '/app/reports/bookings' },
    { label: 'Fleet Utilisation', link: '/app/reports/fleet' },
    { label: 'Revenue Reports', link: '/app/reports/revenue' },
  ]},
  { id: 12, type: 'divider' },
  { id: 13, type: 'title', label: 'ADMINISTRATION' },
  { id: 14, label: 'System Settings', link: '/app/settings', icon: <SettingsIcon /> },
  { id: 15, label: 'My Profile', link: '/app/profile', icon: <ProfileIcon /> },
];

export default structure;
