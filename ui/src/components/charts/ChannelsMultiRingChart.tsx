import React, { useState } from 'react';
import { Layers, Smartphone, CreditCard, ShieldCheck } from 'lucide-react';
import type { ChannelBreakdown } from '../../types';
import { formatCurrency, getChannelLabel } from '../../utils';

interface ChannelsMultiRingChartProps {
  channels: ChannelBreakdown[];
  totalExpense?: number;
  internalTransfersAmount?: number;
  loading?: boolean;
}

export const ChannelsMultiRingChart: React.FC<ChannelsMultiRingChartProps> = ({
  channels,
  totalExpense,
  internalTransfersAmount = 0,
  loading = false,
}) => {
  const [hoveredChannel, setHoveredChannel] = useState<string | null>(null);

  // Geometry for concentric multi-rings
  const size = 180;
  const center = size / 2;
  const strokeWidth = 10;
  const ringGap = 6;

  // Base configurations per channel
  const getRingConfig = (channel: string) => {
    const isYape = channel.includes('YAPE') || channel.includes('PLIN');
    const isCredito = channel.includes('CREDITO');

    if (isYape) {
      return {
        label: 'Yape',
        color: '#9333ea', // Purple-600
        bgTrack: '#f3e8ff', // Purple-100
        gradientId: 'yapeGradient',
        fromColor: '#9333ea',
        toColor: '#d946ef',
        icon: Smartphone,
      };
    }
    if (isCredito) {
      return {
        label: 'Crédito BCP',
        color: '#1d4ed8', // Blue-700
        bgTrack: '#dbeafe', // Blue-100
        gradientId: 'creditoGradient',
        fromColor: '#1d4ed8',
        toColor: '#4f46e5',
        icon: CreditCard,
      };
    }
    return {
      label: 'Débito BCP',
      color: '#0284c7', // Sky-600
      bgTrack: '#e0f2fe', // Sky-100
      gradientId: 'debitoGradient',
      fromColor: '#0284c7',
      toColor: '#38bdf8',
      icon: CreditCard,
    };
  };

  // Compute ring radiuses from outside inward
  // Ring 0: radius 75 (outermost)
  // Ring 1: radius 59
  // Ring 2: radius 43 (innermost)
  const baseRadius = 74;

  const topChannel = channels?.[0];

  return (
    <div className="bg-white rounded-2xl border border-slate-200/80 p-6 flex flex-col justify-between">
      {/* 1. Header */}
      <div className="flex items-center justify-between pb-4 border-b border-slate-100 mb-5">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-blue-50 text-blue-700">
            <Layers className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-sm font-bold text-slate-900">Anillos Concéntricos de Medios de Pago</h3>
            <p className="text-[11px] text-slate-500 font-medium">Volumen relativo por billetera y tarjeta</p>
          </div>
        </div>
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs text-slate-400 animate-pulse">
          Calculando volumen de canales...
        </div>
      ) : !channels || channels.length === 0 ? (
        <div className="py-12 text-center text-xs text-slate-400">
          Sin movimientos en medios de pago este período.
        </div>
      ) : (
        <div>
          <div className="flex flex-col sm:flex-row items-center gap-6">
            {/* 2. Concentric Multi-Ring SVG */}
            <div className="relative shrink-0 flex items-center justify-center">
              <svg width={size} height={size} className="transform -rotate-90">
                <defs>
                  <linearGradient id="yapeGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stopColor="#9333ea" />
                    <stop offset="100%" stopColor="#d946ef" />
                  </linearGradient>
                  <linearGradient id="creditoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stopColor="#1d4ed8" />
                    <stop offset="100%" stopColor="#4f46e5" />
                  </linearGradient>
                  <linearGradient id="debitoGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stopColor="#0284c7" />
                    <stop offset="100%" stopColor="#38bdf8" />
                  </linearGradient>
                </defs>

                {channels.map((c, index) => {
                  const radius = baseRadius - index * (strokeWidth + ringGap);
                  if (radius <= 10) return null;
                  const circ = 2 * Math.PI * radius;
                  const percent = Math.min(100, Math.max(2, c.percentage));
                  const strokeDasharray = `${(percent / 100) * circ} ${circ}`;
                  const config = getRingConfig(c.channel);
                  const isHovered = hoveredChannel === c.channel;

                  return (
                    <g key={c.channel}>
                      {/* Inactive Track */}
                      <circle
                        cx={center}
                        cy={center}
                        r={radius}
                        stroke={config.bgTrack}
                        strokeWidth={strokeWidth}
                        fill="none"
                      />
                      {/* Active Concentric Progress Arc */}
                      <circle
                        cx={center}
                        cy={center}
                        r={radius}
                        stroke={`url(#${config.gradientId})`}
                        strokeWidth={isHovered ? strokeWidth + 3 : strokeWidth}
                        strokeDasharray={strokeDasharray}
                        strokeDashoffset={0}
                        strokeLinecap="round"
                        fill="none"
                        className="transition-all duration-300"
                        onMouseEnter={() => setHoveredChannel(c.channel)}
                        onMouseLeave={() => setHoveredChannel(null)}
                      />
                    </g>
                  );
                })}
              </svg>

              {/* Center Ring Indicator */}
              <div className="absolute inset-0 flex flex-col items-center justify-center text-center pointer-events-none px-2">
                <span className="text-[10px] font-semibold text-slate-400 uppercase tracking-wider">
                  {hoveredChannel ? 'Canal' : 'Canal Líder'}
                </span>
                <span className="text-sm font-extrabold font-num text-slate-900 truncate max-w-[100px]">
                  {hoveredChannel
                    ? channels.find((c) => c.channel === hoveredChannel)?.displayName || 'Canal'
                    : topChannel
                    ? topChannel.channel.includes('YAPE')
                      ? 'Yape'
                      : topChannel.displayName || 'BCP'
                    : 'Sin datos'}
                </span>
                <span className="text-[10px] font-bold text-blue-600 font-num">
                  {hoveredChannel
                    ? formatCurrency(channels.find((c) => c.channel === hoveredChannel)?.amount || 0)
                    : totalExpense !== undefined
                    ? formatCurrency(topChannel?.amount || totalExpense)
                    : `${topChannel?.percentage.toFixed(1) || 0}%`}
                </span>
              </div>
            </div>

            {/* 3. Channels Multi-Ring Legend */}
            <div className="flex-1 w-full space-y-2.5">
              {channels.map((c, index) => {
                const config = getRingConfig(c.channel);
                const Icon = config.icon;
                const isHovered = hoveredChannel === c.channel;

                return (
                  <div
                    key={c.channel}
                    onMouseEnter={() => setHoveredChannel(c.channel)}
                    onMouseLeave={() => setHoveredChannel(null)}
                    className={`p-2.5 rounded-xl border transition-all ${
                      isHovered
                        ? 'bg-slate-50 border-slate-300 shadow-xs'
                        : 'bg-white border-slate-100 hover:bg-slate-50/60'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2 min-w-0">
                        <div
                          className="w-2.5 h-2.5 rounded-full shrink-0"
                          style={{ backgroundColor: config.color }}
                        />
                        <Icon className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                        <span className="text-xs font-bold text-slate-800 truncate">
                          {c.displayName || getChannelLabel(c.channel)}
                        </span>
                      </div>

                      <div className="flex items-center gap-2 shrink-0">
                        <span className="text-xs font-extrabold font-num text-slate-900">
                          {formatCurrency(c.amount)}
                        </span>
                        <span className="text-[10px] font-bold text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded">
                          {c.percentage.toFixed(1)}%
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center justify-between text-[10px] text-slate-400 mt-1 pl-4.5">
                      <span>{c.count} {c.count === 1 ? 'operación' : 'operaciones'}</span>
                      <span className="font-mono text-[9px]">Anillo #{index + 1}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Optional: Internal Transfers Excluded Pill */}
          {internalTransfersAmount > 0 && (
            <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
              <div className="flex items-center gap-1.5">
                <ShieldCheck className="w-4 h-4 text-sky-600" />
                <span>Traspasos propios excluidos:</span>
              </div>
              <span className="font-bold font-num text-slate-700">
                {formatCurrency(internalTransfersAmount)}
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
