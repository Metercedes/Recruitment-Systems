function updatePasswordStrength() {
    const password = document.getElementById('password')?.value || document.getElementById('newPassword')?.value || '';
    const strengthBar = document.getElementById('passwordStrength');
    const strengthText = document.getElementById('passwordStrengthText');

    if (!strengthBar || !strengthText) return;

    let score = 0;
    if (password.length >= 8) score++;
    if (/[A-Z]/.test(password)) score++;
    if (/\d/.test(password)) score++;
    if (/[@#$%^&+=!]/.test(password)) score++;

    strengthBar.className = '';
    strengthBar.classList.add('passwordStrength');

    if (password.length === 0) {
        strengthBar.style.width = '0%';
        strengthText.textContent = '';
    } else if (score <= 2) {
        strengthBar.classList.add('weak');
        strengthText.textContent = 'Weak';
    } else if (score === 3) {
        strengthBar.classList.add('medium');
        strengthText.textContent = 'Medium';
    } else {
        strengthBar.classList.add('strong');
        strengthText.textContent = 'Strong';
    }
}