import React, { useEffect, useRef } from 'react';
import JsBarcode from 'jsbarcode';

interface BarcodeLabelProps {
  value: string;
  label?: string;
  subLabel?: string;
  width?: number;
  height?: number;
  margin?: number;
  fontSize?: number;
  showCodeText?: boolean;
  labelFontSize?: number;
  subLabelFontSize?: number;
  labelAlign?: 'left' | 'center' | 'right';
  subLabelAlign?: 'left' | 'center' | 'right';
}

const BarcodeLabel: React.FC<BarcodeLabelProps> = ({
  value,
  label,
  subLabel,
  width = 2,
  height = 60,
  margin = 0,
  fontSize = 11,
  showCodeText = true,
  labelFontSize = 10,
  subLabelFontSize = 9,
  labelAlign = 'center',
  subLabelAlign = 'center',
}) => {
  const svgRef = useRef<SVGSVGElement>(null);

  useEffect(() => {
    if (!svgRef.current || !value) return;
    try {
      JsBarcode(svgRef.current, value, {
        format: 'CODE128',
        width,
        height,
        displayValue: showCodeText,
        fontSize,
        margin,
        textMargin: 2,
      });
    } catch {
      // invalid barcode value
    }
  }, [value, width, height, margin, fontSize, showCodeText]);

  return (
    <div className="flex flex-col items-center justify-between gap-0.5 w-full h-full overflow-hidden">
      {label && (
        <p
          className="font-black text-slate-700 uppercase tracking-wide leading-tight w-full overflow-hidden"
          style={{ fontSize: `${labelFontSize}px`, textAlign: labelAlign }}
        >
          {label}
        </p>
      )}
      <div className="w-full flex-1 min-h-0 overflow-hidden flex items-center justify-center">
        <svg ref={svgRef} style={{ width: '100%', maxWidth: '100%', display: 'block' }} />
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

export default BarcodeLabel;
