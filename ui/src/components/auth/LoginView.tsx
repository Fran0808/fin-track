import { useState } from 'react';
import { ArrowLeftRight, Loader2, Lock, MailCheck, ShieldCheck, Smartphone, Tags } from 'lucide-react';
import { useAuth } from '../../contexts';
import { LoginWaves } from '../effects/LoginWaves';
import { BrandMark } from '../common/BrandMark';

const HIGHLIGHTS = [
  {
    icon: MailCheck,
    title: 'Captura automática y manual',
    description: 'Lee comprobantes de Gmail y avisos en Android, o registra gastos manuales y concílialos después.',
  },
  {
    icon: ArrowLeftRight,
    title: 'Sin ruido de transferencias',
    description: 'Separa pagos entre tus cuentas o tarjetas propias para ver tu gasto real del mes.',
  },
  {
    icon: Tags,
    title: 'Reglas que clasifican solas',
    description: 'Asigna categorías por comercio o medio de pago y mantén tus períodos ordenados.',
  },
] as const;

const TRUST_POINTS = [
  {
    icon: ShieldCheck,
    text: 'Lectura exclusiva de comprobantes bancarios en Gmail',
  },
  {
    icon: Lock,
    text: 'Nunca pedimos contraseñas ni accesos a tu banco',
  },
  {
    icon: Smartphone,
    text: 'Puedes vincular tu teléfono Android después desde Ajustes',
  },
] as const;

function GoogleMark() {
  return (
    <span
      className="flex h-5 w-5 shrink-0 items-center justify-center rounded-md bg-white"
      aria-hidden="true"
    >
      <svg viewBox="0 0 24 24" className="h-3.5 w-3.5">
        <path
          fill="#4285F4"
          d="M23.49 12.27c0-.79-.07-1.54-.19-2.27H12v4.51h6.47a5.54 5.54 0 0 1-2.4 3.63v3h3.88c2.27-2.09 3.54-5.17 3.54-8.87Z"
        />
        <path
          fill="#34A853"
          d="M12 24c3.24 0 5.95-1.07 7.94-2.91l-3.88-3c-1.07.72-2.45 1.15-4.06 1.15-3.12 0-5.77-2.11-6.72-4.96H1.29v3.11A12 12 0 0 0 12 24Z"
        />
        <path
          fill="#FBBC05"
          d="M5.28 14.28A7.2 7.2 0 0 1 4.9 12c0-.79.14-1.56.38-2.28V6.61H1.29A12 12 0 0 0 0 12c0 1.94.46 3.77 1.29 5.39l3.99-3.11Z"
        />
        <path
          fill="#EA4335"
          d="M12 4.75c1.76 0 3.34.61 4.58 1.8l3.44-3.44C17.95 1.19 15.24 0 12 0A12 12 0 0 0 1.29 6.61l3.99 3.11C6.23 6.86 8.88 4.75 12 4.75Z"
        />
      </svg>
    </span>
  );
}

export function LoginView() {
  const { loginWithGoogle, error } = useAuth();
  const [submitting, setSubmitting] = useState(false);

  const handleLogin = async () => {
    try {
      setSubmitting(true);
      await loginWithGoogle();
    } catch {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col bg-canvas text-ink">
      <header className="mx-auto flex w-full max-w-7xl items-center justify-between px-6 py-6 sm:px-10">
        <div className="flex items-center">
          <BrandMark className="mr-3" />
          <span className="font-display text-lg font-bold tracking-tight">FinTrack</span>
        </div>
        <span className="hidden text-xs font-medium text-muted sm:inline">Web y Android</span>
      </header>

      <main className="mx-auto grid w-full max-w-7xl flex-1 items-center gap-12 px-6 py-10 sm:px-10 lg:grid-cols-[1.15fr_0.85fr] lg:gap-16">
        <div className="relative isolate max-w-2xl">
          <LoginWaves />
          <p className="eyebrow">Registro automático de gastos e ingresos</p>
          <h1 className="font-display mt-4 text-[clamp(2.5rem,4.8vw,4.15rem)] font-semibold leading-[1.06] tracking-tight">
            Tus gastos al día,{' '}
            <span className="block text-brand">sin anotarlos a mano.</span>
          </h1>
          <p className="mt-5 max-w-xl text-base leading-relaxed text-muted sm:text-[1.0625rem]">
            FinTrack convierte tus correos bancarios y notificaciones móviles en un registro ordenado por categoría, medio de pago y período — sin pedir tus claves bancarias.
          </p>

          <ul className="mt-9 grid gap-5 border-t border-line pt-7 sm:grid-cols-3">
            {HIGHLIGHTS.map(({ icon: Icon, title, description }) => (
              <li key={title} className="space-y-2">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand/8 text-brand">
                  <Icon className="h-4 w-4" aria-hidden="true" />
                </div>
                <h2 className="text-sm font-semibold leading-snug text-ink">{title}</h2>
                <p className="text-xs leading-relaxed text-muted">{description}</p>
              </li>
            ))}
          </ul>
        </div>

        <section
          className="surface w-full max-w-[26.5rem] p-7 sm:p-9 lg:justify-self-end"
          aria-labelledby="login-heading"
        >
          <p className="eyebrow">Tu espacio personal</p>
          <h2 id="login-heading" className="font-display mt-3 text-2xl font-semibold">
            Entra a tu panel
          </h2>
          <p className="mt-2 text-sm leading-relaxed text-muted">
            Usa tu cuenta de Google para abrir tu panel y sincronizar solo tus comprobantes bancarios de Gmail.
          </p>

          {error && (
            <p
              role="alert"
              className="mt-6 rounded-xl border border-negative/20 bg-negative/5 px-4 py-3 text-sm text-negative"
            >
              {error}
            </p>
          )}

          <button
            type="button"
            onClick={handleLogin}
            disabled={submitting}
            className="mt-7 flex w-full items-center justify-center gap-3 rounded-xl bg-brand px-5 py-3.5 text-sm font-semibold text-white shadow-xs transition-colors hover:bg-[#244788] disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <GoogleMark />}
            {submitting ? 'Conectando con Google...' : 'Continuar con Google'}
          </button>

          <ul className="mt-6 space-y-2.5 border-t border-line pt-5 text-xs leading-relaxed text-muted">
            {TRUST_POINTS.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-start gap-2.5">
                <Icon className="mt-0.5 h-3.5 w-3.5 shrink-0 text-positive" aria-hidden="true" />
                <span>{text}</span>
              </li>
            ))}
          </ul>
        </section>
      </main>

      <footer className="mx-auto flex w-full max-w-7xl flex-wrap items-center justify-between gap-2 border-t border-line px-6 py-5 text-xs text-muted sm:px-10">
        <span>FinTrack · Seguimiento personal de flujo de dinero</span>
        <span>Gastos, entradas y balance neto por período</span>
      </footer>
    </div>
  );
}
