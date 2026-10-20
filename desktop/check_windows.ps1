Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public class User32 {
    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);
    [DllImport("user32.dll")]
    public static extern int GetWindowText(IntPtr hWnd, System.Text.StringBuilder lpString, int nMaxCount);
    [DllImport("user32.dll")]
    public static extern int GetClassName(IntPtr hWnd, System.Text.StringBuilder lpClassName, int nMaxCount);
}
'@

$pids = (Get-Process -Name SyncSwift, msedgewebview2 -ErrorAction SilentlyContinue).Id
Write-Host "Monitoring PIDs: $($pids -join ', ')"

[User32]::EnumWindows([User32+EnumWindowsProc]{
    param($hwnd, $lparam)
    $wpid = 0
    [User32]::GetWindowThreadProcessId($hwnd, [ref]$wpid) | Out-Null
    if ($pids -contains $wpid) {
        $sbText = New-Object System.Text.StringBuilder 256
        [User32]::GetWindowText($hwnd, $sbText, 256) | Out-Null
        $sbClass = New-Object System.Text.StringBuilder 256
        [User32]::GetClassName($hwnd, $sbClass, 256) | Out-Null
        $vis = [User32]::IsWindowVisible($hwnd)
        Write-Host ("PID: {0} | HWND: {1} | Visible: {2} | Class: {3} | Title: {4}" -f $wpid, $hwnd, $vis, $sbClass.ToString(), $sbText.ToString())
    }
    return $true
}, [IntPtr]::Zero) | Out-Null
