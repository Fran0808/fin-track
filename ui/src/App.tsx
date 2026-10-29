import { useCallback, useState } from 'react';
import { SidebarNavigation, PeriodSelector } from './components/layout';
import type { NavView } from './components/layout';
import { HomeView, MyMoneyView, TransactionsView, SettingsView, CategoriesView, ClassificationRulesView } from './components/views';
import { TransactionDetailModal } from './components/transactions';
import { CreateClassificationRuleModal } from './components/transactions/CreateClassificationRuleModal';
import { LoginView } from './components/auth';
import { PairDeviceModal } from './components/auth/PairDeviceModal';
import { useAuth } from './contexts';
import { useFinance } from './hooks';
import type { ClassificationRuleDraft, Transaction, UserProfile } from './types';
import { LogOut, Menu, Loader2, Smartphone } from 'lucide-react';
import { SyncStatus } from './components/layout/SyncStatus';
import { MobileNavigation } from './components/layout/MobileNavigation';
import { useDashboardNavigation } from './hooks/useDashboardNavigation';

const VIEW_LABELS: Record<NavView, string> = {
  inicio: 'Inicio',
  'mi-dinero': 'Análisis',
  movimientos: 'Movimientos',
  reglas: 'Reglas',
  categorias: 'Categorías',
  configuracion: 'Configuración',
};

export function App() {
  const { user, loading: loadingAuth, logout } = useAuth();

  if (loadingAuth) {
    return (
      <div className="flex min-h-screen items-center justify-center gap-3 bg-canvas text-muted">
        <Loader2 className="h-5 w-5 animate-spin text-brand" />
        <p className="text-sm">Verificando sesión...</p>
      </div>
    );
  }

  if (!user) return <LoginView />;

  return <AuthenticatedDashboard key={user.id} user={user} logout={logout} />;
}

function AuthenticatedDashboard({ user, logout }: { user: UserProfile; logout: () => void }) {
  const {
    periodAnalytics,
    transactionsPage,
    recentTransactions,
    loadingRecent,
    loadingSummary,
    loadingTransactions,
    syncStatus,
    syncStatusUnavailable,
    isSyncing,
    triggerEmailSync,
    refreshSyncStatus,
    error,
    filters,
    transactionFilters,
    selectedPeriod,
    setSelectedPeriod,
    handlePageChange,
    handleFilterChange,
    showMerchantTransactions,
    showCategoryTransactions,
    refreshAll,
  } = useFinance(user.id);

  const [activeView, setActiveView] = useDashboardNavigation();
  const [selectedTransaction, setSelectedTransaction] = useState<Transaction | null>(null);
  const [ruleDraft, setRuleDraft] = useState<ClassificationRuleDraft | null>(null);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [pairingOpen, setPairingOpen] = useState(false);
  const [introCompletedFor, setIntroCompletedFor] = useState<number | null>(null);
  const closeMobileMenu = useCallback(() => setMobileMenuOpen(false), []);

  return (
    <div className="flex min-h-screen bg-canvas text-ink">
      <MobileNavigation open={mobileMenuOpen} onClose={closeMobileMenu} activeView={activeView} onViewChange={setActiveView} />
      <div className="sticky top-0 hidden h-dvh shrink-0 lg:flex">
        <SidebarNavigation
          activeView={activeView}
          onViewChange={(view) => {
            setActiveView(view);
            setMobileMenuOpen(false);
          }}
        />
      </div>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="border-b border-line bg-white">
          <div className="mx-auto flex min-h-20 max-w-[1440px] flex-wrap items-center justify-between gap-3 px-5 py-3 sm:px-8 lg:px-10">
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
                aria-label={mobileMenuOpen ? 'Cerrar menú' : 'Abrir menú'}
                aria-expanded={mobileMenuOpen}
                aria-haspopup="dialog"
                className="rounded-lg p-2 text-ink hover:bg-canvas lg:hidden"
              >
                <Menu className="h-5 w-5" />
              </button>
              <p className="font-display text-lg font-semibold leading-tight">{VIEW_LABELS[activeView]}</p>
            </div>

            <div className="flex flex-wrap items-center gap-3 sm:gap-5">
              <button
                type="button"
                onClick={() => setPairingOpen(true)}
                aria-haspopup="dialog"
                className="inline-flex items-center gap-2 rounded-lg border border-line px-3 py-2 text-sm font-medium text-ink hover:bg-canvas focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand"
              >
                <Smartphone className="h-4 w-4 text-brand" aria-hidden="true" />
                Vinculación móvil
              </button>
              <SyncStatus status={syncStatus} unavailable={syncStatusUnavailable} syncing={isSyncing} onSync={() => { void triggerEmailSync().catch(() => {}); }} />
              {!['configuracion', 'categorias', 'reglas'].includes(activeView) && !(activeView === 'movimientos' && (filters.allHistory || filters.startDate)) && <PeriodSelector
                year={selectedPeriod.year}
                month={selectedPeriod.month}
                onChange={(year, month) => setSelectedPeriod({ year, month })}
              />}
              <div className="hidden h-8 w-px bg-line sm:block" />
              <div className="flex items-center gap-2.5">
                {user.pictureUrl ? (
                  <img src={user.pictureUrl} alt="" className="h-9 w-9 rounded-full border border-line object-cover" />
                ) : (
                  <div className="flex h-9 w-9 items-center justify-center rounded-full bg-brand/10 text-sm font-semibold text-brand">
                    {(user.fullName || user.email).charAt(0).toUpperCase()}
                  </div>
                )}
                <span className="hidden max-w-36 truncate text-sm font-medium md:block">{user.fullName || user.email}</span>
                <button type="button" onClick={logout} aria-label="Cerrar sesión" title="Cerrar sesión" className="rounded-lg p-2 text-muted hover:bg-canvas hover:text-ink">
                  <LogOut className="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>
        </header>

        <main className="mx-auto w-full max-w-[1440px] flex-1 px-5 py-8 sm:px-8 lg:px-10 lg:py-10">
          {error && <p role="alert" className="mb-6 rounded-xl border border-negative/20 bg-negative/5 px-4 py-3 text-sm text-negative">{error}</p>}
          {activeView === 'inicio' && (
            <HomeView
              analytics={periodAnalytics}
              recentTransactions={recentTransactions}
              loading={loadingSummary || loadingRecent}
              onNavigateToTransactions={() => setActiveView('movimientos')}
              onSelectTransaction={setSelectedTransaction}
              selectedYear={selectedPeriod.year}
              selectedMonth={selectedPeriod.month}
              animateIntro={introCompletedFor !== user.id}
              onIntroComplete={() => setIntroCompletedFor(user.id)}
            />
          )}
          {activeView === 'mi-dinero' && (
            <MyMoneyView
              analytics={periodAnalytics}
              loading={loadingSummary}
              selectedYear={selectedPeriod.year}
              selectedMonth={selectedPeriod.month}
              onSelectMerchant={periodAnalytics?.periodStart && periodAnalytics?.periodEnd ? merchantName => {
                showMerchantTransactions(merchantName, periodAnalytics.periodStart!, periodAnalytics.periodEnd!);
                setActiveView('movimientos');
              } : undefined}
              onSelectCategory={periodAnalytics?.periodStart && periodAnalytics?.periodEnd ? category => {
                showCategoryTransactions(category, periodAnalytics.periodStart!, periodAnalytics.periodEnd!);
                setActiveView('movimientos');
              } : undefined}
            />
          )}
          {activeView === 'movimientos' && (
            <TransactionsView
              pageData={transactionsPage}
              loading={loadingTransactions}
              filters={filters}
              exportFilters={transactionFilters}
              onFilterChange={handleFilterChange}
              onPageChange={handlePageChange}
              onSelectTransaction={setSelectedTransaction}
              onCreated={transaction => { setSelectedTransaction(transaction); void refreshAll(); }}
              periodName={`${selectedPeriod.month.toString().padStart(2, '0')}/${selectedPeriod.year}`}
            />
          )}
          {activeView === 'categorias' && (
            <CategoriesView />
          )}
          {activeView === 'reglas' && <ClassificationRulesView onHistoryApplied={() => { void refreshAll(); }} />}
          {activeView === 'configuracion' && (
            <SettingsView
              googleStatus={syncStatus}
              statusUnavailable={syncStatusUnavailable}
              syncing={isSyncing}
              onSync={triggerEmailSync}
              onStatusRefresh={refreshSyncStatus}
              onOpenPairing={() => setPairingOpen(true)}
            />
          )}
        </main>
      </div>

      <TransactionDetailModal transaction={selectedTransaction} onClose={() => setSelectedTransaction(null)} onCreateRule={setRuleDraft} onUpdated={transaction => {
        setSelectedTransaction(current => current?.id === selectedTransaction?.id ? transaction : current);
        void refreshAll();
      }} />
      {ruleDraft && <CreateClassificationRuleModal draft={ruleDraft} onClose={() => setRuleDraft(null)} />}
      <PairDeviceModal isOpen={pairingOpen} onClose={() => setPairingOpen(false)} />
    </div>
  );
}

export default App;
