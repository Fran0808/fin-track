import React from 'react';
import {
  PiggyBank,
  HeartHandshake,
  Car,
  Utensils,
  ShoppingBag,
  Sparkles,
  Bike,
  Dumbbell,
  GraduationCap,
  Users,
  PawPrint,
  HeartPulse,
  Tv,
  Ticket,
  Home,
  Laptop,
  Plane,
  Shirt,
  Briefcase,
  FileText,
  Gift,
  ShieldCheck,
  Wine,
  ArrowLeftRight,
  HelpCircle,
  Tag,
  Coffee,
  Fuel,
  BookOpen,
} from 'lucide-react';

interface CategoryIconProps {
  name?: string | null;
  className?: string;
}

const ICON_MAP: Record<string, React.ComponentType<{ className?: string }>> = {
  'piggy-bank': PiggyBank,
  'heart-handshake': HeartHandshake,
  car: Car,
  utensils: Utensils,
  'shopping-bag': ShoppingBag,
  sparkles: Sparkles,
  bike: Bike,
  dumbbell: Dumbbell,
  'graduation-cap': GraduationCap,
  users: Users,
  'paw-print': PawPrint,
  'heart-pulse': HeartPulse,
  tv: Tv,
  ticket: Ticket,
  home: Home,
  laptop: Laptop,
  plane: Plane,
  shirt: Shirt,
  briefcase: Briefcase,
  'file-text': FileText,
  gift: Gift,
  'shield-check': ShieldCheck,
  wine: Wine,
  'arrow-left-right': ArrowLeftRight,
  'help-circle': HelpCircle,
  coffee: Coffee,
  fuel: Fuel,
  'book-open': BookOpen,
  tag: Tag,
};

export const AVAILABLE_ICONS = [
  { id: 'tag', label: 'General', component: Tag },
  { id: 'utensils', label: 'Comida', component: Utensils },
  { id: 'coffee', label: 'Café', component: Coffee },
  { id: 'car', label: 'Auto', component: Car },
  { id: 'bike', label: 'Delivery / Bici', component: Bike },
  { id: 'shopping-bag', label: 'Compras', component: ShoppingBag },
  { id: 'piggy-bank', label: 'Ahorro', component: PiggyBank },
  { id: 'heart-pulse', label: 'Salud', component: HeartPulse },
  { id: 'home', label: 'Hogar', component: Home },
  { id: 'laptop', label: 'Tecnología', component: Laptop },
  { id: 'tv', label: 'Suscripciones', component: Tv },
  { id: 'plane', label: 'Viajes', component: Plane },
  { id: 'dumbbell', label: 'Fitness', component: Dumbbell },
  { id: 'briefcase', label: 'Trabajo', component: Briefcase },
  { id: 'graduation-cap', label: 'Educación', component: GraduationCap },
  { id: 'sparkles', label: 'Cuidado', component: Sparkles },
  { id: 'paw-print', label: 'Mascotas', component: PawPrint },
  { id: 'ticket', label: 'Salidas', component: Ticket },
  { id: 'wine', label: 'Bares', component: Wine },
  { id: 'gift', label: 'Regalos', component: Gift },
];

export function CategoryIcon({ name, className = 'h-5 w-5' }: CategoryIconProps) {
  const IconComponent = (name && ICON_MAP[name.toLowerCase()]) || Tag;
  return <IconComponent className={className} />;
}
