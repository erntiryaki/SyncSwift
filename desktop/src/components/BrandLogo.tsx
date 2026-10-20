import React from 'react';

interface BrandLogoProps {
  className?: string;
  isDark?: boolean;
}

export const BrandLogo: React.FC<BrandLogoProps> = ({ className = 'h-8', isDark = true }) => {
  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      <img
        src="./assets/logo-icon/app-icon.png"
        alt="SyncSwift Icon"
        className="w-8 h-8 rounded-xl object-contain drop-shadow-sm"
      />
      <span className={`text-[20px] font-extrabold tracking-tight ${isDark ? 'text-white' : 'text-[#121829]'}`}>
        SyncSwift
      </span>
    </div>
  );
};

export const BrandIcon: React.FC<{ className?: string }> = ({ className = 'w-6 h-6' }) => {
  return (
    <img
      src="./assets/logo-icon/app-icon.png"
      alt="SyncSwift"
      className={`${className} object-contain`}
    />
  );
};
