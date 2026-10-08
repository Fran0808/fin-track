import { useState } from 'react';
import { SidebarNavigation, PeriodSelector } from './components/layout';
import type { NavView } from './components/layout';
import { HomeView, MyMoneyView, TransactionsView, PlaceholderView, SettingsView, InstrumentsView, CategoriesView } from './components/views';
import { TransactionDetailModal } from './components/transactions';
import { LoginView } from './components/auth';
import { PairDeviceModal } from './components/auth/PairDeviceModal';
import { useAuth } from './contexts';
import { useFinance } from './hooks';
import type { Transaction, UserProfile } from './types';
import { LogOut, Menu, X, Loader2, Smartphone } from 'lucide-react';
import { SyncStatus } from './components/layout/SyncStatus';

const VIEW_LABELS: Record<NavView, string> = {
  inicio: 'Inicio',
  'mi-dinero': 'Mi dinero',
  movimientos: 'Movimientos',
  tarjetas: 'Tarjetas y cuentas',
  categorias: 'Categorías',
  presupuestos: 'Presupuestos',
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
    refreshAll,
  } = useFinance(user.id);

  const [activeView, setActiveView] = useState<NavView>('inicio');
  const [selectedTransaction, setSelectedTransaction] = useState<Transaction | null>(null);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [pairingOpen, setPairingOpen] = useState(false);
  const [introCompletedFor, setIntroCompletedFor] = useState<number | null>(null);
  const [instrumentRevision, setInstrumentRevision] = useState(0);

  return (
    <div className="flex min-h-screen bg-canvas text-ink">
      {mobileMenuOpen && (
        <button
          type="button"
          aria-label="Cerrar menú"
          className="fixed inset-0 z-40 bg-ink/40 lg:hidden"
          onClick={() => setMobileMenuOpen(false)}
        />
      )}

      <div className={`fixed inset-y-0 left-0 z-50 flex transition-transform duration-200 lg:sticky lg:top-0 lg:h-screen lg:translate-x-0 ${mobileMenuOpen ? 'translate-x-0' : '-translate-x-full'}`}>
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
                className="rounded-lg p-2 text-ink hover:bg-canvas lg:hidden"
              >
                {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
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
              {activeView !== 'configuracion' && activeView !== 'categorias' && <PeriodSelector
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
              recentTransactions={transactionsPage?.content || []}
              loading={loadingSummary || loadingTransactions}
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
              periodName={`${selectedPeriod.month.toString().padStart(2, '0')}/${selectedPeriod.year}`}
            />
          )}
          {activeView === 'categorias' && (
            <CategoriesView />
          )}
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
          {activeView === 'tarjetas' && (
            <InstrumentsView year={selectedPeriod.year} month={selectedPeriod.month} revision={instrumentRevision} onSelectTransaction={setSelectedTransaction} onProductsChanged={() => { void refreshAll(); }} />
          )}
          {activeView === 'presupuestos' && (
            <PlaceholderView view={activeView} onNavigateHome={() => setActiveView('inicio')} />
          )}
        </main>
      </div>

      <TransactionDetailModal transaction={selectedTransaction} onClose={() => setSelectedTransaction(null)} onUpdated={transaction => {
        setSelectedTransaction(current => current?.id === transaction.id ? transaction : current);
        setInstrumentRevision(value => value + 1);
        void refreshAll();
      }} />
      <PairDeviceModal isOpen={pairingOpen} onClose={() => setPairingOpen(false)} />
    </div>
  );
}

export default App;
