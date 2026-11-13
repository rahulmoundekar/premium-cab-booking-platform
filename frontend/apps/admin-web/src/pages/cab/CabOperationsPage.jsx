import React from 'react';
import {
  Alert, Box, Button, Card, CardContent, Chip, Grid, Paper, Stack,
  Table, TableBody, TableCell, TableHead, TableRow, Typography
} from '@mui/material';
import { Add, ArrowForward, DirectionsCar, EventAvailable, Payments, Groups } from '@mui/icons-material';

const metricCards = [
  { label: 'Bookings today', value: '—', note: 'Connect booking service', icon: <EventAvailable /> },
  { label: 'Available vehicles', value: '—', note: 'Live fleet status pending', icon: <DirectionsCar /> },
  { label: 'Drivers on duty', value: '—', note: 'Driver shift service pending', icon: <Groups /> },
  { label: 'Revenue today', value: '—', note: 'Payment integration pending', icon: <Payments /> },
];

const demoRows = [
  ['SB-DEMO-001', 'Airport transfer', 'Pune → Mumbai Airport', 'Executive Sedan', 'Awaiting API'],
  ['SB-DEMO-002', 'Outstation', 'Pune → Mahabaleshwar', 'Premium Executive SUV', 'Awaiting API'],
  ['SB-DEMO-003', 'Group transit', 'Pune city circuit', 'Force Urbania', 'Awaiting API'],
];

export default function CabOperationsPage({ title = 'Operations', description = 'Manage Suabhi Cab Booking operations.' , dashboard = false }) {
  return <Box sx={{ p: { xs: 1, md: 2 } }}>
    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }} spacing={2} sx={{ mb: 3 }}>
      <Box>
        <Typography variant="h4" fontWeight={700}>{dashboard ? 'Operations Dashboard' : title}</Typography>
        <Typography color="text.secondary" sx={{ mt: 0.5 }}>{dashboard ? 'Pune & Mumbai · Fleet, bookings and service operations' : description}</Typography>
      </Box>
      <Button variant="contained" startIcon={<Add />} disabled>{dashboard ? 'Create booking (next phase)' : 'Add new (next phase)'}</Button>
    </Stack>
    <Alert severity="info" sx={{ mb: 3 }}>
      Template preview: the supplied React Material Admin UI is connected to the project structure. Live business data and write actions will be enabled as the booking and identity APIs are implemented. No sample rows below represent real customers or reservations.
    </Alert>
    {dashboard && <Grid container spacing={2} sx={{ mb: 3 }}>
      {metricCards.map((metric) => <Grid size={{ xs: 12, sm: 6, xl: 3 }} key={metric.label}>
        <Card sx={{ height: '100%' }}><CardContent>
          <Stack direction="row" justifyContent="space-between" alignItems="center">
            <Typography color="text.secondary" variant="body2">{metric.label}</Typography>
            <Box sx={{ color: 'primary.main' }}>{metric.icon}</Box>
          </Stack>
          <Typography variant="h3" fontWeight={700} sx={{ my: 1 }}>{metric.value}</Typography>
          <Typography variant="caption" color="text.secondary">{metric.note}</Typography>
        </CardContent></Card>
      </Grid>)}
    </Grid>}
    <Paper sx={{ p: 2, mb: 3 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h6" fontWeight={700}>{dashboard ? 'Booking activity preview' : `${title} workspace`}</Typography>
        <Chip size="small" color="warning" label="API integration pending" />
      </Stack>
      {dashboard ? <Table size="small">
        <TableHead><TableRow><TableCell>Reference</TableCell><TableCell>Trip type</TableCell><TableCell>Route</TableCell><TableCell>Vehicle class</TableCell><TableCell>Status</TableCell></TableRow></TableHead>
        <TableBody>{demoRows.map((row) => <TableRow key={row[0]}>{row.map((cell) => <TableCell key={cell}>{cell === 'Awaiting API' ? <Chip size="small" label={cell} /> : cell}</TableCell>)}</TableRow>)}</TableBody>
      </Table> : <Box sx={{ py: 5, textAlign: 'center' }}>
        <Typography variant="h6">{title} module scaffold</Typography>
        <Typography color="text.secondary" sx={{ maxWidth: 620, mx: 'auto', mt: 1 }}>
          This page uses the imported Material UI theme, cards, responsive grid and navigation. The domain forms, filters, permissions and API-backed tables will be implemented with the corresponding backend module.
        </Typography>
        <Button sx={{ mt: 2 }} endIcon={<ArrowForward />} disabled>Module implementation planned for next milestone</Button>
      </Box>}
    </Paper>
  </Box>;
}
