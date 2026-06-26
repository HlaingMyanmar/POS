import React from 'react';

interface QrCodeLabelProps {
  value: string;
  label?: string;
  subLabel?: string;
  pixelSize?: number;
  labelFontSize?: number;
  subLabelFontSize?: number;
  labelAlign?: 'left' | 'center' | 'right';
  subLabelAlign?: 'left' | 'center' | 'right';
}

const QrCodeLabel: React.FC<QrCodeLabelProps> = ({
  value,
  label,
  subLabel,
  pixelSize = 220,
  labelFontSize = 10,
  subLabelFontSize = 9,
  labelAlign = 'center',
  subLabelAlign = 'center',
}) => {
  const src = `https://api.qrserver.com/v1/create-qr-code/?size=${pixelSize}x${pixelSize}&data=${encodeURIComponent(value)}`;

  return (
    <div className="flex flex-col items-center justify-between gap-1 w-full h-full overflow-hidden">
      {label && (
        <p
          className="font-black text-slate-700 uppercase tracking-wide leading-tight w-full overflow-hidden"
          style={{ fontSize: `${labelFontSize}px`, textAlign: labelAlign }}
        >
          {label}
        </p>
      )}
      <div className="w-full flex-1 min-h-0 overflow-hidden flex items-center justify-center">
        <img
          src={src}
          alt={`QR ${value}`}
          width={pixelSize}
          height={pixelSize}
          className="object-contain max-w-full max-h-full"
          style={{ width: '100%', height: '100%' }}
          loading="eager"
        />
      </div>
      {subLabel && (
        <p
          className="text-slate-500 font-semibold w-full overflow-hidden"
          style={{ fontSize: `${subLabelFontSize}px`, textAlign: subLabelAlign }}
        >
          {subLabel}
        </p>
      )}
    </div>
  );
};

export default QrCodeLabel;
