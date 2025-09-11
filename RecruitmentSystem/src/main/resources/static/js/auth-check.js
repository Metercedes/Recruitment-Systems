// Client-side authentication check and redirect
function checkAuthAndRedirect() {
    const currentUser = JSON.parse(localStorage.getItem('currentUser') || 'null');
    const currentPath = window.location.pathname;
    
    // Define protected pages and their required roles
    const protectedPages = {
        '/add-job.html': ['admin', 'employer'],
        '/match-applicants.html': ['admin', 'employer'],
        '/ratings.html': ['jobseeker'],
        '/settings.html': ['admin', 'employer', 'jobseeker'],
        '/index.html': ['admin', 'employer', 'jobseeker']
    };
    
    // Check if current page requires authentication
    if (protectedPages[currentPath]) {
        if (!currentUser) {
            console.log('[Auth] No user logged in, redirecting to login');
            window.location.href = '/jobseeker-login.html';
            return false;
        }
        
        const requiredRoles = protectedPages[currentPath];
        const userRole = currentUser.isAdmin ? 'admin' : currentUser.userType;
        
        if (!requiredRoles.includes(userRole)) {
            console.log('[Auth] User role not authorized for this page');
            document.body.innerHTML = `
                <div style="text-align: center; margin-top: 50px;">
                    <h2>Access Denied</h2>
                    <p>You don't have permission to access this page.</p>
                    <a href="/index.html">Return to Home</a>
                </div>
            `;
            return false;
        }
    }
    
    return true;
}

// Enhanced session validation
function validateSession() {
    const currentUser = JSON.parse(localStorage.getItem('currentUser') || 'null');
    if (!currentUser) {
        return false;
    }
    
    // Check session with server
    return fetch('/api/session-time', {
        headers: { 'X-Username': currentUser.username }
    })
    .then(response => {
        if (response.status === 401) {
            console.log('[Session] Server session expired');
            clearSession();
            return false;
        }
        return response.json();
    })
    .then(data => {
        if (data && data.remainingTime <= 0) {
            console.log('[Session] Session expired');
            clearSession();
            return false;
        }
        return true;
    })
    .catch(error => {
        console.error('[Session] Validation error:', error);
        clearSession();
        return false;
    });
}

function clearSession() {
    localStorage.removeItem('currentUser');
    const currentPath = window.location.pathname;
    
    // Don't redirect if already on login page
    if (!currentPath.includes('login.html')) {
        window.location.href = '/jobseeker-login.html';
    }
}

// Initialize auth check on page load
document.addEventListener('DOMContentLoaded', function() {
    checkAuthAndRedirect();
});