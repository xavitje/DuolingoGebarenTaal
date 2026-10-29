export function LicenseExpired({ schoolName }: { schoolName: string }) {
  return (
    <div class="page page-narrow stack-lg" style={{ textAlign: 'center', alignItems: 'center' }}>
      <h1 class="title">Je school heeft even geen toegang</h1>
      <p class="lead">De licentie van {schoolName} is verlopen. Vraag je juf of meester wat je kunt doen.</p>
    </div>
  );
}
