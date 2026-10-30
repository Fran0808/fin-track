import { useEffect, useRef, useState } from 'react';
import { Check, X, ChevronRight, Loader2, Pencil, History, ListPlus } from 'lucide-react';
import { animate } from 'animejs';
import type { ClassificationRuleDraft, Transaction } from '../../types';
import { formatCurrency, formatDate, getChannelLabel, getTransactionFlowLabel, isOwnCardPayment } from '../../utils';
import { api } from '../../services/api';
import { useCategories } from '../../hooks';
import { CustomSelect } from '../common';
import { TransactionOriginPanel } from './TransactionOriginPanel';
import { ManualReconciliationPanel } from './ManualReconciliationPanel';
import { TransactionCorrectionPanel } from './TransactionCorrectionPanel';

interface TransactionDetailModalProps {
  presentation?: 'modal' | 'sidebar';
  startEditing?: boolean;
  onBusyChange?: (busy: boolean) => void;
  transaction: Transaction | null;
  closing?: boolean;
  onClosing?: () => void;
  onClose: () => void;
  onUpdated?: (transaction: Transaction) => void;
  onCreateRule?: (draft: ClassificationRuleDraft) => void;
}

const isInstantMotion = typeof window !== 'undefined' && (
  (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) ||
  navigator.userAgent.includes('jsdom')
);

export function TransactionDetailModal(props: TransactionDetailModalProps) {
  return props.transaction ? <TransactionDetailContent {...props} transaction={props.transaction} /> : null;
}

function TransactionDetailContent({ transaction, closing = false, onClosing, onClose, onUpdated, onCreateRule, presentation = 'modal', startEditing = false, onBusyChange }: TransactionDetailModalProps & { transaction: Transaction }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const openedAs = useRef<'modal' | 'sidebar' | null>(null);
  const isClosing = useRef(false);
  const prevTxId = useRef(transaction.id);
  const [wideScreen, setWideScreen] = useState(() => typeof window.matchMedia === 'function' && window.matchMedia('(min-width: 1280px)').matches);
  const { options: categoryOptions, loading: loadingCategories, error: categoryError, refresh: refreshCategories } = useCategories(true);

  // Classification state
  const [category, setCategory] = useState<string>(transaction.category || '');
  const [savedCategory, setSavedCategory] = useState(transaction.category || '');
  const [isSaving, setIsSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [reconciling, setReconciling] = useState(false);
  const [correcting, setCorrecting] = useState(false);
  const [correctionMode, setCorrectionMode] = useState<'edit' | 'history' | null>(() => startEditing && presentation === 'sidebar' && transaction.reconciledToId == null ? 'edit' : null);
  const busy = isSaving || reconciling || correcting;
  const [archived, setArchived] = useState(transaction.reconciledToId != null);
  const readOnly = transaction.reconciledToId != null || archived;
  const docked = presentation === 'sidebar' && wideScreen;

  const triggerClose = () => {
    if (busy || isClosing.current) return;
    const dialog = dialogRef.current;
    if (!dialog) {
      onClose();
      return;
    }

    isClosing.current = true;
    onClosing?.();

    if (isInstantMotion) {
      dialog.close();
      onClose();
      return;
    }

    if (docked) {
      animate(dialog, {
        translateX: ['0%', '100%'],
        duration: 250,
        ease: 'outCubic',
        onComplete: () => {
          dialog.close();
          onClose();
        },
      });
    } else {
      animate(dialog, {
        scale: [1, 0.96],
        opacity: [1, 0],
        duration: 160,
        ease: 'inQuad',
        onComplete: () => {
          dialog.close();
          onClose();
        },
      });
    }
  };

  useEffect(() => {
    if (prevTxId.current !== transaction.id) {
      prevTxId.current = transaction.id;
      isClosing.current = false;
      setCategory(transaction.category || '');
      setSavedCategory(transaction.category || '');
      setSaveSuccess(false);
      setSaveError(null);
      setIsSaving(false);
      setReconciling(false);
      setCorrecting(false);
      setCorrectionMode(startEditing && presentation === 'sidebar' && transaction.reconciledToId == null ? 'edit' : null);
      setArchived(transaction.reconciledToId != null);

      const dialog = dialogRef.current;
      if (dialog && !isInstantMotion) {
        animate(dialog, {
          translateX: '0%',
          duration: 150,
          ease: 'outCubic',
        });
      }
    }
  }, [transaction, startEditing, presentation]);

  useEffect(() => {
    if (startEditing && presentation === 'sidebar' && transaction.reconciledToId == null) {
      setCorrectionMode('edit');
    }
  }, [startEditing, presentation, transaction.reconciledToId]);

  useEffect(() => {
    if (closing && !isClosing.current) {
      triggerClose();
    }
  }, [closing]);

  useEffect(() => {
    if (presentation !== 'sidebar' || typeof window.matchMedia !== 'function') return;
    const media = window.matchMedia('(min-width: 1280px)');
    const change = () => setWideScreen(media.matches);
    media.addEventListener('change', change);
    return () => media.removeEventListener('change', change);
  }, [presentation]);

  useEffect(() => {
    onBusyChange?.(busy);
    return () => onBusyChange?.(false);
  }, [busy, onBusyChange]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    const mode = docked ? 'sidebar' : 'modal';
    if (dialog.open && openedAs.current !== mode) dialog.close();
    if (!dialog.open) {
      if (docked) dialog.show(); else dialog.showModal();
      openedAs.current = mode;

      if (!isInstantMotion) {
        if (docked) {
          animate(dialog, {
            translateX: ['100%', '0%'],
            duration: 250,
            ease: 'outCubic',
          });
        } else {
          animate(dialog, {
            scale: [0.96, 1],
            opacity: [0, 1],
            duration: 200,
            ease: 'outCubic',
          });
        }
      }
    }
  }, [docked]);

  const handleSaveClassification = async () => {
    if (busy || readOnly || category === savedCategory) return;
    setIsSaving(true);
    setSaveError(null);
    setSaveSuccess(false);
    try {
      const updated = await api.updateTransactionClassification(transaction.id, {
        category,
      });
      setSavedCategory(updated.category || '');
      setSaveSuccess(true);
      window.setTimeout(() => setSaveSuccess(false), 2500);
      if (onUpdated) onUpdated(updated);
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Error al guardar cambios');
    } finally {
      setIsSaving(false);
    }
  };

  if (!transaction) return null;

  const isIncome = transaction.flowType === 'INCOME';
  const isTransfer = transaction.flowType === 'INTERNAL_TRANSFER';
  const flowLabel = getTransactionFlowLabel(transaction);
  const ownCardPayment = isOwnCardPayment(transaction);

  return (
    <dialog
      ref={dialogRef}
      id="transaction-detail-panel"
      aria-modal={!docked}
      onClose={event => {
        if (!event.currentTarget.open && !isClosing.current) onClose();
      }}
      onCancel={event => {
        if (busy) {
          event.preventDefault();
          return;
        }
        event.preventDefault();
        triggerClose();
      }}
      onKeyDown={event => {
        if (!docked || event.key !== 'Escape' || busy || event.defaultPrevented) return;
        const field = event.target instanceof Element ? event.target.closest('select, input') : null;
        if (field && (!CSS.supports('selector(:open)') || field.matches(':open'))) return;
        event.preventDefault();
        triggerClose();
      }}
      onClick={event => {
        if (docked || busy || event.target !== dialogRef.current) return;
        const bounds = event.currentTarget.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) {
          triggerClose();
        }
      }}
      aria-labelledby="transaction-dialog-title"
      className={presentation === 'sidebar'
        ? 'fixed inset-y-0 right-0 left-auto z-40 m-0 h-dvh max-h-none w-[min(100%,420px)] max-w-none overflow-y-auto border-0 border-l border-line bg-white p-0 text-ink shadow-2xl backdrop:bg-ink/30 will-change-transform'
        : 'm-auto max-h-[92dvh] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-[20px] border border-line bg-white p-0 text-ink shadow-xl backdrop:bg-ink/40 will-change-transform'}
    >
      <div className="flex items-start justify-between gap-4 border-b border-line px-6 py-4">
        <div>
          <p className="eyebrow">{flowLabel}</p>
          <h2 id="transaction-dialog-title" className="font-display mt-0.5 text-lg font-semibold">{correctionMode === 'edit' ? 'Editar movimiento' : 'Detalle del movimiento'}</h2>
          {correctionMode && <p className="mt-1 text-xs text-muted">{transaction.contactName}</p>}
        </div>
        <div className="flex items-center gap-1">
          {presentation !== 'sidebar' && onUpdated && !readOnly && <button type="button" disabled={busy} onClick={() => setCorrectionMode('edit')} aria-label="Corregir movimiento" title="Corregir movimiento" className="rounded-lg p-2 text-brand hover:bg-canvas disabled:opacity-50"><Pencil className="h-5 w-5" /></button>}
          <button type="button" disabled={busy} onClick={() => setCorrectionMode('history')} aria-label="Historial de correcciones" title="Historial de correcciones" className="rounded-lg p-2 text-muted hover:bg-canvas disabled:opacity-50"><History className="h-5 w-5" /></button>
          <button type="button" onClick={triggerClose} disabled={busy} aria-label="Cerrar detalle" title="Cerrar detalle" className="rounded-lg p-1.5 text-muted hover:bg-canvas hover:text-ink">
            {presentation === 'sidebar' ? <ChevronRight className="h-5 w-5" /> : <X className="h-5 w-5" />}
          </button>
        </div>
      </div>

      <div className="px-6 py-5">
        {correctionMode ? <fieldset disabled={busy}><TransactionCorrectionPanel key={correctionMode} transactionId={transaction.id} mode={correctionMode} onBack={presentation === 'sidebar' && correctionMode === 'edit' ? undefined : () => setCorrectionMode(null)} onBusyChange={setCorrecting} onSaved={updated => { setCorrectionMode(null); onUpdated?.(updated); }} /></fieldset> : <>
        <p className={`font-display font-num text-3xl font-semibold tracking-tight ${isIncome ? 'text-positive' : isTransfer ? 'text-brand' : 'text-ink'}`}>
          {isIncome ? '+ ' : isTransfer ? '' : '− '}{formatCurrency(transaction.amount)}
        </p>
        <p className="mt-1 text-sm font-medium">{transaction.contactName || 'Movimiento'}</p>
        {ownCardPayment && isTransfer && <p role="status" className="mt-1.5 text-xs text-muted">No suma gastos ni entradas y no afecta el flujo neto.</p>}

        <dl className="mt-4 grid grid-cols-2 gap-x-6 gap-y-3 border-y border-line py-3.5 text-sm">
          <div>
            <dt className="text-xs text-muted">Medio</dt>
            <dd className="mt-0.5 font-medium">{getChannelLabel(transaction.channel)}{transaction.cardLast4 ? ` ··${transaction.cardLast4}` : ''}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Fecha y hora</dt>
            <dd className="mt-0.5 font-medium">{formatDate(transaction.transactionDate)}</dd>
          </div>
          <TransactionOriginPanel transactionId={transaction.id} />
        </dl>

        {onUpdated && transaction.captureOrigin && ['MANUAL','EMAIL','PHONE'].includes(transaction.captureOrigin) && <ManualReconciliationPanel transaction={transaction} onUpdated={onUpdated} onBusyChange={setReconciling} onArchivedChange={setArchived} />}
        {readOnly && <p role="status" className="mt-3 text-xs text-muted">Este registro manual se conserva como antecedente y ya no suma en los totales.</p>}

        <fieldset disabled={busy || readOnly} className="mt-4">
          <div className="space-y-3">
            <div>
              <label htmlFor="modal-category" className="block text-xs font-semibold text-muted">
                Categoría
              </label>
              <div className="mt-1 flex items-center gap-2">
                <div className="min-w-0 flex-1">
                <CustomSelect
                  id="modal-category"
                  disabled={loadingCategories || !!categoryError || busy}
                  value={category}
                  onChange={val => { setCategory(val); setSaveSuccess(false); setSaveError(null); }}
                  options={[
                    { value: '', label: 'Sin categorizar' },
                    ...(category && !categoryOptions.some((o) => o.id === category)
                      ? [{ value: category, label: category }]
                      : []),
                    ...categoryOptions.map((cat) => ({ value: cat.id, label: cat.label })),
                  ]}
                />
                </div>
                {!readOnly && !isTransfer && transaction.contactName?.trim() && onCreateRule && <button type="button" disabled={busy}
                  onClick={() => onCreateRule({ matchText: transaction.contactName.trim(), categoryName: category })}
                  aria-label="Crear regla para este comercio" title="Crear regla para este comercio"
                  className="shrink-0 rounded-lg p-2 text-brand hover:bg-canvas focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand disabled:opacity-50"><ListPlus className="h-5 w-5" /></button>}
              </div>
              {categoryError && <p role="alert" className="mt-1.5 text-xs text-negative">{categoryError} <button type="button" onClick={() => void refreshCategories()} className="underline">Reintentar categorías</button></p>}
            </div>

            {saveError && (
              <p role="alert" className="text-xs font-medium text-negative">
                {saveError}
              </p>
            )}

            {(saveSuccess || category !== savedCategory) && <div className="flex flex-wrap items-center justify-end gap-3">
                {saveSuccess && <span role="status" className="animate-pop mr-auto inline-flex items-center gap-1 text-xs font-semibold text-positive">
                    <Check className="h-3.5 w-3.5" /> Cambios guardados
                  </span>}
              {category !== savedCategory && <button
                type="button"
                onClick={handleSaveClassification}
                disabled={busy}
                className="inline-flex items-center gap-1.5 rounded-xl bg-brand px-3.5 py-1.5 text-xs font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
              >
                {isSaving && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                Guardar cambios
              </button>}
            </div>}
          </div>
        </fieldset>

      </>}
      </div>
    </dialog>
  );
}
