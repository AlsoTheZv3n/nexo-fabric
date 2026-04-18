// calculateRisk — Berechnet Risk-Score basierend auf Umsatz + überfälligen Rechnungen
// Input: Customer object properties
// Output: { score: "low"|"medium"|"high", reason: string }

var revenue = object.revenue || 0;
var overdue = object.overdueInvoiceCount || 0;

if (overdue >= 3 || revenue < 50000) {
  return JSON.stringify({
    score: "high",
    reason: "3+ überfällige Rechnungen oder Umsatz < 50k"
  });
}
if (overdue >= 1 || revenue < 150000) {
  return JSON.stringify({
    score: "medium",
    reason: "Erste Warnsignale — überfällige Rechnung oder niedriger Umsatz"
  });
}
return JSON.stringify({
  score: "low",
  reason: "Kunde in gutem Zustand"
});
