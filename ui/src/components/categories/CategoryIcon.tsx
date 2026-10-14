import { Tag } from 'lucide-react';
import { ICON_MAP } from './categoryIconsData';

export { AVAILABLE_ICONS } from './categoryIconsData';

interface CategoryIconProps {
  name?: string | null;
  className?: string;
}

export function CategoryIcon({ name, className = 'h-5 w-5' }: CategoryIconProps) {
  const IconComponent = (name && ICON_MAP[name.toLowerCase()]) || Tag;
  return <IconComponent className={className} />;
}

