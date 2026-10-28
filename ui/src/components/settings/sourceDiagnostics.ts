import type { GoogleAuthStatus } from '../../types';

export function emailFailureAdvice(status: GoogleAuthStatus): string {
  switch (status.lastSyncFailureCode) {
    case 'AUTH_REQUIRED': return 'Google requiere una nueva autorización. Vuelve a conectar Gmail para reanudar la lectura.';
    case 'ACCESS_DENIED': return 'Google rechazó el acceso al correo. Revisa los permisos de Gmail al volver a conectar; si continúa, revisa la configuración de acceso de Google.';
    case 'RATE_LIMITED': return 'Google limitó temporalmente las consultas. Espera unos minutos antes de sincronizar nuevamente.';
    case 'TEMPORARY': return 'No se pudo completar la conexión con Google. Comprueba la conexión del servidor e inténtalo más tarde.';
    case 'PROCESSING': return 'Algunos correos no pudieron procesarse. Los movimientos guardados se conservan. Reintenta la sincronización; si continúa, solicita una revisión del procesamiento.';
    default: return 'No se pudo completar la sincronización de correo. La causa no está disponible. Reintenta y, si continúa, solicita una revisión de la conexión.';
  }
}
