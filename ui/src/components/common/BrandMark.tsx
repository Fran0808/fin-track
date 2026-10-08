import mark from '../../assets/fintrack-mark.png';

export function BrandMark({ className = '' }: { className?: string }) {
  return <img src={mark} alt="" aria-hidden="true" width={40} height={40} className={`h-10 w-10 shrink-0 object-contain ${className}`} />;
}
