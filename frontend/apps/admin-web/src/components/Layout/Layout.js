import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import useStyles from './styles';
import Header from '../Header';
import Sidebar from '../Sidebar';
import BreadCrumbs from '../BreadCrumbs';
import { useLayoutState } from '../../context/LayoutContext';
import structure from '../Sidebar/SidebarStructure';
import CabOperationsPage from '../../pages/cab/CabOperationsPage';

const modulePages = [
  ['bookings', 'Bookings Overview'], ['dispatch', 'Dispatch & Trips'], ['fleet', 'Fleet Management'],
  ['pricing', 'Pricing & Rate Plans'], ['payments', 'Payments & Refunds'],
  ['bookings/all', 'All Bookings'], ['bookings/upcoming', 'Upcoming Bookings'],
  ['bookings/in-progress', 'In-progress Bookings'], ['bookings/completed', 'Completed Bookings'],
  ['bookings/cancellations', 'Booking Cancellations'], ['dispatch/board', 'Dispatch Board'],
  ['dispatch/trips', 'Trip Monitor'], ['fleet/vehicles', 'Vehicle Inventory'],
  ['fleet/categories', 'Vehicle Categories'], ['fleet/models', 'Vehicle Models'],
  ['fleet/documents', 'Vehicle Documents & Compliance'], ['fleet/maintenance', 'Fleet Maintenance'],
  ['drivers', 'Driver Directory'], ['drivers/onboarding', 'Driver Onboarding & Verification'],
  ['drivers/shifts', 'Driver Shifts'], ['customers', 'Customer Directory'],
  ['pricing/fare-plans', 'Fare Plans'], ['pricing/extras', 'Surcharges & Allowances'],
  ['pricing/coupons', 'Coupons & Promotions'], ['payments/transactions', 'Payment Transactions'],
  ['payments/refunds', 'Refunds'], ['payments/settlements', 'Settlement Reconciliation'],
  ['corporate', 'Corporate Accounts'], ['service-areas', 'Service Areas & Coverage'],
  ['reports', 'Reports & Analytics'], ['reports/bookings', 'Booking Reports'],
  ['reports/fleet', 'Fleet Utilisation Reports'], ['reports/revenue', 'Revenue Reports'],
  ['settings', 'System Settings'], ['profile', 'My Profile']
];

function Layout() {
  const classes = useStyles();
  const layoutState = useLayoutState();
  return <div className={classes.root}>
    <Header />
    <Sidebar structure={structure} />
    <div className={`${classes.content} ${layoutState.isSidebarOpened ? classes.contentShift : ''}`}>
      <div className={classes.fakeToolbar} />
      <BreadCrumbs />
      <Routes>
        <Route path="dashboard" element={<CabOperationsPage dashboard />} />
        {modulePages.map(([path, title]) => <Route key={path} path={path} element={<CabOperationsPage title={title} />} />)}
        <Route path="*" element={<Navigate to="/app/dashboard" replace />} />
      </Routes>
    </div>
  </div>;
}

export default Layout;
