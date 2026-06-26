import React, { useState } from 'react';
import {
  Building2, Phone, Mail, MapPin, CreditCard,
  CheckCircle, ArrowRight, ArrowLeft, Loader2,
  Banknote, Smartphone, Wallet, ChevronRight,
} from 'lucide-react';
import { setupService, SetupInitDTO } from '../services/api';

interface Props {
  onComplete: () => void;
}

const STEPS = ['Company Info', 'Payment Methods', 'Done'];

const PM_OPTIONS = [
  { name: 'Cash',     icon: <Banknote   size={18} />, desc: 'ငွေသားပေးချေမှု',      required: true  },
  { name: 'KBZ Bank', icon: <CreditCard size={18} />, desc: 'KBZ ဘဏ်အကောင့်',       required: false },
  { name: 'KPay',     icon: <Smartphone size={18} />, desc: 'KBZ KPay Mobile',       required: false },
  { name: 'Wave Pay', icon: <Wallet     size={18} />, desc: 'Wave Money Mobile Pay', required: false },
];

const Field = ({
  label, icon, value, onChange, placeholder, type = 'text', required,
}: {
  label: string; icon: React.ReactNode; value: string;
  onChange: (v: string) => void; placeholder?: string;
  type?: string; required?: boolean;
}) => (
  <div className="space-y-1">
    <label className="text-xs font-semibold text-slate-600 flex items-center gap-1">
      {icon} {label} {required && <span className="text-rose-500">*</span>}
    </label>
    <input
      type={type}
      value={value}
      onChange={e => onChange(e.target.value)}
      placeholder={placeholder}
      className="w-full border border-slate-200 rounded-lg px-3 py-2 text-sm text-slate-700 focus:outline-none focus:border-indigo-400 focus:ring-1 focus:ring-indigo-200 placeholder:text-slate-300"
    />
  </div>
);

const SetupWizardPage: React.FC<Props> = ({ onComplete }) => {
  const [step, setStep]       = useState(0);
  const [saving, setSaving]   = useState(false);
  const [error, setError]     = useState('');

  // Step 1
  const [companyName,    setCompanyName]    = useState('');
  const [companyAddress, setCompanyAddress] = useState('');
  const [companyPhone,   setCompanyPhone]   = useState('');
  const [companyEmail,   setCompanyEmail]   = useState('');

  // Step 2
  const [selectedPM, setSelectedPM] = useState<string[]>(['Cash', 'KBZ Bank']);

  const togglePM = (name: string, required: boolean) => {
    if (required) return;
    setSelectedPM(prev =>
      prev.includes(name) ? prev.filter(p => p !== name) : [...prev, name]
    );
  };

  const canNext = () => {
    if (step === 0) return companyName.trim().length >= 2;
    return true;
  };

  const handleFinish = async () => {
    setSaving(true);
    setError('');
    try {
      const dto: SetupInitDTO = {
        companyName:    companyName.trim(),
        companyAddress: companyAddress.trim(),
        companyPhone:   companyPhone.trim(),
        companyEmail:   companyEmail.trim(),
        paymentMethods: selectedPM,
      };
      await setupService.initialize(dto);
      setStep(2);
    } catch (e: any) {
      setError(e?.response?.data?.message || e?.message || 'Setup failed. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-900 via-slate-800 to-indigo-900 flex items-center justify-center p-4">
      <div className="w-full max-w-lg">

        {/* Header */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-14 h-14 bg-indigo-500/20 border border-indigo-400/30 rounded-2xl mb-4">
            <Building2 size={26} className="text-indigo-400" />
          </div>
          <h1 className="text-2xl font-bold text-white">Welcome to SSPD</h1>
          <p className="text-slate-400 text-sm mt-1">Set up your business in 2 quick steps</p>
        </div>

        {/* Step Indicator */}
        <div className="flex items-center justify-center gap-2 mb-6">
          {STEPS.map((s, i) => (
            <React.Fragment key={s}>
              <div className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-bold transition-all ${
                i === step
                  ? 'bg-indigo-500 text-white'
                  : i < step
                  ? 'bg-emerald-500/20 text-emerald-400'
                  : 'bg-slate-700/60 text-slate-500'
              }`}>
                {i < step
                  ? <CheckCircle size={11} />
                  : <span className="w-4 h-4 rounded-full border border-current flex items-center justify-center text-[9px]">{i + 1}</span>
                }
                {s}
              </div>
              {i < STEPS.length - 1 && (
                <ChevronRight size={12} className="text-slate-600" />
              )}
            </React.Fragment>
          ))}
        </div>

        {/* Card */}
        <div className="bg-white rounded-2xl shadow-2xl overflow-hidden">

          {/* Step 0 — Company Info */}
          {step === 0 && (
            <div className="p-6 space-y-4">
              <div>
                <h2 className="text-base font-bold text-slate-800">Company Information</h2>
                <p className="text-xs text-slate-500 mt-0.5">This will appear on all invoices and receipts.</p>
              </div>
              <Field label="Company Name" icon={<Building2 size={12} />} value={companyName}
                onChange={setCompanyName} placeholder="e.g. SSPD IT Solution Center" required />
              <Field label="Address" icon={<MapPin size={12} />} value={companyAddress}
                onChange={setCompanyAddress} placeholder="Street, Township, City" />
              <Field label="Phone" icon={<Phone size={12} />} value={companyPhone}
                onChange={setCompanyPhone} placeholder="09-xxxxxxxxx" type="tel" />
              <Field label="Email" icon={<Mail size={12} />} value={companyEmail}
                onChange={setCompanyEmail} placeholder="info@company.com" type="email" />
            </div>
          )}

          {/* Step 1 — Payment Methods */}
          {step === 1 && (
            <div className="p-6 space-y-4">
              <div>
                <h2 className="text-base font-bold text-slate-800">Payment Methods</h2>
                <p className="text-xs text-slate-500 mt-0.5">
                  Select how your customers pay. These link to your accounting accounts automatically.
                </p>
              </div>
              <div className="space-y-2">
                {PM_OPTIONS.map(opt => {
                  const selected = selectedPM.includes(opt.name);
                  return (
                    <button
                      key={opt.name}
                      onClick={() => togglePM(opt.name, opt.required)}
                      className={`w-full flex items-center gap-3 p-3 rounded-xl border-2 text-left transition-all ${
                        selected
                          ? 'border-indigo-400 bg-indigo-50'
                          : 'border-slate-100 bg-slate-50 hover:border-slate-200'
                      } ${opt.required ? 'cursor-default' : 'cursor-pointer'}`}
                    >
                      <div className={`p-2 rounded-lg ${selected ? 'bg-indigo-100 text-indigo-600' : 'bg-white text-slate-400'}`}>
                        {opt.icon}
                      </div>
                      <div className="flex-1">
                        <p className="text-sm font-semibold text-slate-700">{opt.name}</p>
                        <p className="text-[11px] text-slate-400">{opt.desc}</p>
                      </div>
                      <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center transition-all ${
                        selected ? 'border-indigo-500 bg-indigo-500' : 'border-slate-200'
                      }`}>
                        {selected && <CheckCircle size={12} className="text-white" />}
                      </div>
                      {opt.required && (
                        <span className="text-[10px] font-bold text-indigo-500 bg-indigo-50 px-2 py-0.5 rounded-full border border-indigo-200">
                          Required
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>
              <p className="text-[10px] text-slate-400">
                You can always add more payment methods later in Settings.
              </p>
            </div>
          )}

          {/* Step 2 — Done */}
          {step === 2 && (
            <div className="p-8 text-center space-y-4">
              <div className="inline-flex items-center justify-center w-16 h-16 bg-emerald-100 rounded-full mb-2">
                <CheckCircle size={32} className="text-emerald-500" />
              </div>
              <h2 className="text-lg font-bold text-slate-800">Setup Complete!</h2>
              <p className="text-sm text-slate-500">
                Your business is ready. Payment methods have been linked to your accounting accounts automatically.
              </p>
              <div className="bg-slate-50 rounded-xl p-4 text-left space-y-1">
                {[
                  `Company: ${companyName}`,
                  `Payment Methods: ${selectedPM.join(', ')}`,
                  'COA accounts: Auto-linked ✓',
                  'Ready to create sales & reports ✓',
                ].map(item => (
                  <div key={item} className="flex items-center gap-2 text-xs text-slate-600">
                    <CheckCircle size={11} className="text-emerald-500 shrink-0" />
                    {item}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Error */}
          {error && (
            <div className="mx-6 mb-4 bg-rose-50 border border-rose-200 text-rose-700 text-xs rounded-lg px-4 py-2">
              {error}
            </div>
          )}

          {/* Footer Buttons */}
          <div className={`px-6 pb-6 flex ${step === 0 ? 'justify-end' : 'justify-between'}`}>
            {step > 0 && step < 2 && (
              <button onClick={() => setStep(s => s - 1)}
                className="flex items-center gap-1.5 text-sm font-semibold text-slate-500 hover:text-slate-700 px-4 py-2 rounded-lg hover:bg-slate-50">
                <ArrowLeft size={14} /> Back
              </button>
            )}

            {step === 0 && (
              <button onClick={() => setStep(1)} disabled={!canNext()}
                className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-bold px-5 py-2 rounded-lg disabled:opacity-40">
                Next <ArrowRight size={14} />
              </button>
            )}

            {step === 1 && (
              <button onClick={handleFinish} disabled={saving || selectedPM.length === 0}
                className="flex items-center gap-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-bold px-5 py-2 rounded-lg disabled:opacity-40">
                {saving ? <Loader2 size={14} className="animate-spin" /> : null}
                {saving ? 'Saving...' : 'Finish Setup'}
              </button>
            )}

            {step === 2 && (
              <button onClick={onComplete}
                className="w-full flex items-center justify-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-bold px-5 py-2.5 rounded-lg">
                Go to Dashboard <ArrowRight size={14} />
              </button>
            )}
          </div>
        </div>

        <p className="text-center text-xs text-slate-500 mt-4">
          You can change these settings anytime in Company Settings.
        </p>
      </div>
    </div>
  );
};

export default SetupWizardPage;
