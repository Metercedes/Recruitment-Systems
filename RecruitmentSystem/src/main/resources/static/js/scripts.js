let currentUser = null;
let sessionTimer = null;

function login(userType) {
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const recaptchaToken = grecaptcha ? grecaptcha.getResponse() : 'mock-recaptcha-token'; // Mock for testing
    if (!username || !password || !recaptchaToken) {
        console.log('[Login] Empty fields or reCAPTCHA');
        alert('Please fill all fields and complete reCAPTCHA');
        return;
    }
    fetch('/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, recaptchaToken })
    })
        .then(response => {
            console.log('[Login] HTTP status:', response.status);
            return response.json();
        })
        .then(data => {
            console.log('[Login] Response:', data);
            if (data.success) {
                currentUser = { username: data.username, isAdmin: data.isAdmin, userType: data.userType };
                localStorage.setItem('currentUser', JSON.stringify(currentUser));
                startSessionTimer(data.sessionTimeout);
                const redirectUrl = data.wasLocked ? '/change-password.html' : '/index.html';
                console.log('[Login] Redirecting to:', redirectUrl);
                window.location.href = redirectUrl;
            } else {
                alert(data.message);
            }
        })
        .catch(error => {
            console.error('[Login] Error:', error);
            alert('Error: ' + error.message);
        });
}

function register() {
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    const urlParams = new URLSearchParams(window.location.search);
    const userType = urlParams.get('type') || 'jobseeker';
    if (!username || !password) {
        console.log('[Register] Empty fields');
        alert('Please fill all fields');
        return;
    }
    fetch('/api/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, userType })
    })
        .then(response => response.json())
        .then(data => {
            alert(data.message);
            if (data.success) {
                window.location.href = userType === 'employer' ? '/employer-login.html' : '/jobseeker-login.html';
            }
        })
        .catch(error => {
            console.error('[Register] Error:', error);
            alert('Error: ' + error.message);
        });
}

function changePassword() {
    const oldPassword = document.getElementById('oldPassword').value;
    const newPassword = document.getElementById('newPassword').value;
    const errorMessage = document.getElementById('errorMessage');
    if (!oldPassword || !newPassword) {
        console.log('[ChangePassword] Empty fields');
        if (errorMessage) {
            errorMessage.style.display = 'block';
            errorMessage.textContent = 'Please fill all fields';
        }
        return;
    }
    if (!currentUser) {
        console.log('[ChangePassword] No current user');
        alert('Please log in');
        window.location.href = userType === 'employer' ? '/employer-login.html' : '/jobseeker-login.html';
        return;
    }
    fetch('/api/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: currentUser.username, oldPassword, newPassword })
    })
        .then(response => response.json())
        .then(data => {
            alert(data.message);
            if (data.success) {
                if (errorMessage) errorMessage.style.display = 'none';
                window.location.href = '/index.html';
            } else if (errorMessage) {
                errorMessage.style.display = 'block';
                errorMessage.textContent = data.message;
            }
        })
        .catch(error => {
            console.error('[ChangePassword] Error:', error);
            if (errorMessage) {
                errorMessage.style.display = 'block';
                errorMessage.textContent = 'Error: ' + error.message;
            }
        });
}

function logout() {
    if (!currentUser) {
        console.log('[logout] No current user, redirecting to /login.html');
        window.location.href = currentUser && currentUser.userType === 'employer' ? '/employer-login.html' : '/jobseeker-login.html';
        return;
    }
    console.log('[logout] Attempting to log out user:', currentUser.username);
    fetch('/api/logout', {
        method: 'POST',
        headers: { 'X-Username': currentUser.username }
    })
        .then(response => response.json())
        .then(data => {
            console.log('[logout] Response:', data);
            clearSession();
            window.location.href = currentUser.userType === 'employer' ? '/employer-login.html' : '/jobseeker-login.html';
        })
        .catch(error => {
            console.error('[logout] Error:', error);
            clearSession();
            window.location.href = currentUser.userType === 'employer' ? '/employer-login.html' : '/jobseeker-login.html';
        });
}

function clearSession() {
    console.log('[clearSession] Clearing session');
    currentUser = null;
    localStorage.removeItem('currentUser');
    if (sessionTimer) {
        clearInterval(sessionTimer);
        sessionTimer = null;
    }
}

function startSessionTimer(seconds) {
    let timeLeft = seconds;
    const timerElement = document.getElementById('sessionTimer');
    if (!timerElement) {
        console.log('[startSessionTimer] sessionTimer element not found');
        return;
    }
    updateTimerDisplay(timeLeft);
    if (sessionTimer) clearInterval(sessionTimer);
    sessionTimer = setInterval(() => {
        timeLeft--;
        updateTimerDisplay(timeLeft);
        if (timeLeft <= 0) {
            console.log('[startSessionTimer] Session timeout, logging out');
            logout();
        }
    }, 1000);
}

function updateTimerDisplay(seconds) {
    const timerElement = document.getElementById('sessionTimer');
    if (!timerElement) {
        console.log('[updateTimerDisplay] sessionTimer element not found');
        return;
    }
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    timerElement.textContent = `Session expires in ${minutes}:${secs.toString().padStart(2, '0')}`;
    console.log(`[updateTimerDisplay] Updated timer: ${minutes}:${secs.toString().padStart(2, '0')}`);
}

function checkSession() {
    if (!currentUser) {
        console.log('[checkSession] No current user');
        return;
    }
    fetch('/api/session-time', {
        headers: { 'X-Username': currentUser.username }
    })
        .then(response => {
            console.log('[checkSession] HTTP status:', response.status);
            if (response.status === 401) {
                console.log('[checkSession] Session expired via API');
                logout();
                return null;
            }
            return response.json();
        })
        .then(data => {
            if (data && data.remainingTime <= 0) {
                console.log('[checkSession] Session expired via remainingTime');
                logout();
            } else if (data) {
                console.log('[checkSession] Remaining time:', data.remainingTime);
            }
        })
        .catch(error => {
            console.error('[checkSession] Error:', error);
            logout();
        });
}

function addJob() {
    if (!currentUser) {
        console.log('[AddJob] No current user');
        alert('Please log in');
        window.location.href = currentUser && currentUser.userType === 'employer' ? '/employer-login.html' : '/jobseeker-login.html';
        return;
    }
    if (!currentUser.isAdmin && currentUser.userType !== 'employer') {
        console.log('[AddJob] User not authorized');
        document.getElementById('errorMessage').style.display = 'block';
        document.getElementById('errorMessage').textContent = 'Only admins or employers can add jobs';
        return;
    }
    const title = document.getElementById('jobTitle').value.trim();
    const description = document.getElementById('jobDesc').value.trim();
    const requirements = document.getElementById('jobReqs').value.split(',').map(r => r.trim().toLowerCase()).filter(r => r);
    const salaryRange = document.getElementById('salaryRange').value.trim();
    const difficulty = document.getElementById('difficulty').value.trim();
    const requiredSkills = document.getElementById('requiredSkills').value.split(',').map(s => s.trim().toLowerCase()).filter(s => s);
    const benefits = document.getElementById('benefits').value.split(',').map(b => b.trim()).filter(b => b);
    if (!title || !description || !requirements.length || !salaryRange || !difficulty || !requiredSkills.length || !benefits.length) {
        console.log('[AddJob] Empty fields');
        document.getElementById('errorMessage').style.display = 'block';
        document.getElementById('errorMessage').textContent = 'Please fill all fields';
        return;
    }
    fetch('/api/jobs', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Username': currentUser.username
        },
        body: JSON.stringify({ title, description, requirements, salaryRange, difficulty, requiredSkills, benefits })
    })
        .then(response => response.json())
        .then(data => {
            if (data.message === 'Job added') {
                document.getElementById('addJobForm').reset();
                document.getElementById('errorMessage').style.display = 'none';
                alert('Job added successfully');
            } else {
                document.getElementById('errorMessage').style.display = 'block';
                document.getElementById('errorMessage').textContent = data.message;
            }
        })
        .catch(error => {
            console.error('[AddJob] Error:', error);
            document.getElementById('errorMessage').style.display = 'block';
            document.getElementById('errorMessage').textContent = 'Error: ' + error.message;
        });
}

function registerApplicant() {
    const name = document.getElementById('applicantName').value.trim();
    const skills = document.getElementById('applicantSkills').value.split(',').map(s => s.trim().toLowerCase()).filter(s => s);
    if (!name || !skills.length) {
        console.log('[RegisterApplicant] Empty fields');
        alert('Please fill all fields');
        return;
    }
    fetch('/api/applicants', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, skills })
    })
        .then(response => response.json())
        .then(data => {
            alert(data.message);
            if (data.message === 'Applicant registered') {
                document.getElementById('applicantName').value = '';
                document.getElementById('applicantSkills').value = '';
            }
        })
        .catch(error => {
            console.error('[RegisterApplicant] Error:', error);
            alert('Error: ' + error.message);
        });
}

function viewJobs() {
    const salaryRange = document.getElementById('filterSalary')?.value || '';
    const difficulty = document.getElementById('filterDifficulty')?.value || '';
    const skill = document.getElementById('filterSkill')?.value || '';
    const minRating = parseFloat(document.getElementById('filterRating')?.value) || null;
    fetch(`/api/jobs?salaryRange=${encodeURIComponent(salaryRange)}&difficulty=${encodeURIComponent(difficulty)}&skill=${encodeURIComponent(skill)}&minRating=${minRating || ''}`, {
        headers: currentUser ? { 'X-Username': currentUser.username } : {}
    })
        .then(response => response.json())
        .then(jobs => {
            const jobList = document.getElementById('jobList');
            jobList.innerHTML = '';
            if (jobs.length === 0) {
                jobList.innerHTML = '<li>No jobs available</li>';
            } else {
                jobs.forEach(job => {
                    const li = document.createElement('li');
                    li.innerHTML = `
                    ${job.title} - ${job.description}<br>
                    Salary: ${job.salaryRange}, Difficulty: ${job.difficulty}<br>
                    Skills: ${job.requiredSkills.join(', ')}<br>
                    Benefits: ${job.benefits.join(', ')}<br>
                    Employer: ${job.employerUsername} (Rating: ${job.employerRating.toFixed(1)})<br>
                    <textarea id="comment-${job.id}" placeholder="Add a comment"></textarea>
                    <button onclick="addComment('${job.id}')">Comment</button>
                    <div id="comments-${job.id}"></div>
                `;
                    jobList.appendChild(li);
                    fetch(`/api/comments?jobId=${job.id}`, {
                        headers: currentUser ? { 'X-Username': currentUser.username } : {}
                    })
                        .then(res => res.json())
                        .then(comments => {
                            const commentDiv = document.getElementById(`comments-${job.id}`);
                            comments.forEach(comment => {
                                const p = document.createElement('p');
                                p.textContent = `${comment.commenter}: ${comment.content} (${comment.timestamp})`;
                                commentDiv.appendChild(p);
                            });
                        });
                });
            }
        })
        .catch(error => {
            console.error('[ViewJobs] Error:', error);
            alert('Error fetching jobs: ' + error.message);
        });
}

function showMatches() {
    if (!currentUser) {
        console.log('[ShowMatches] No current user');
        window.location.href = currentUser && currentUser.userType === 'employer' ? '/employer-login.html' : '/jobseeker-login.html';
        return;
    }
    fetch('/api/matches', {
        headers: { 'X-Username': currentUser.username }
    })
        .then(response => response.json())
        .then(matches => {
            const matchList = document.getElementById('matchList');
            matchList.innerHTML = '';
            if (matches.length === 0) {
                matchList.innerHTML = '<li>No matches available</li>';
            } else {
                matches.forEach(match => {
                    const li = document.createElement('li');
                    li.textContent = match;
                    matchList.appendChild(li);
                });
            }
        })
        .catch(error => {
            console.error('[ShowMatches] Error:', error);
            alert('Error fetching matches: ' + error.message);
        });
}

function checkAuth() {
    currentUser = JSON.parse(localStorage.getItem('currentUser'));
    if (!currentUser) {
        console.log('[CheckAuth] No current user, redirecting');
        window.location.href = currentUser && currentUser.userType === 'employer' ? '/employer-login.html' : '/jobseeker-login.html';
        return;
    }
    if (window.location.pathname.includes('add-job.html') && !currentUser.isAdmin && currentUser.userType !== 'employer') {
        console.log('[CheckAuth] User not authorized for add-job.html');
        document.body.innerHTML = '<h2>Only admins or employers can access this page</h2><a href="/index.html">Back to Home</a>';
    }
    checkSession();
}

function submitRating() {
    if (!currentUser) {
        window.location.href = '/jobseeker-login.html';
        return;
    }
    const targetUsername = document.getElementById('targetUsername').value.trim();
    const score = parseInt(document.getElementById('ratingScore').value);
    const review = document.getElementById('ratingReview').value.trim();
    fetch('/api/ratings', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Username': currentUser.username
        },
        body: JSON.stringify({ target: targetUsername, score, review })
    })
        .then(response => response.json())
        .then(data => {
            alert(data.message);
            if (data.success) {
                document.getElementById('ratingForm').reset();
            }
        })
        .catch(error => {
            alert('Error: ' + error.message);
        });
}

function viewRatings() {
    const targetUsername = document.getElementById('viewTargetUsername').value.trim();
    fetch(`/api/ratings?target=${targetUsername}`, {
        headers: { 'X-Username': currentUser.username }
    })
        .then(response => response.json())
        .then(ratings => {
            const ratingsList = document.getElementById('ratingsList');
            ratingsList.innerHTML = '';
            if (ratings.length === 0) {
                ratingsList.innerHTML = '<p>No ratings available</p>';
            } else {
                ratings.forEach(rating => {
                    const div = document.createElement('div');
                    div.textContent = `${rating.rater}: ${rating.score}/5 - ${rating.review} (${rating.timestamp})`;
                    ratingsList.appendChild(div);
                });
            }
        })
        .catch(error => {
            alert('Error fetching ratings: ' + error.message);
        });
}

function addComment(jobId) {
    if (!currentUser) {
        window.location.href = '/jobseeker-login.html';
        return;
    }
    const content = document.getElementById(`comment-${jobId}`).value.trim();
    if (!content) {
        alert('Comment cannot be empty');
        return;
    }
    fetch('/api/comments', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Username': currentUser.username
        },
        body: JSON.stringify({ jobId, content })
    })
        .then(response => response.json())
        .then(data => {
            alert(data.message);
            if (data.success) {
                document.getElementById(`comment-${jobId}`).value = '';
                viewJobs();
            }
        })
        .catch(error => {
            alert('Error: ' + error.message);
        });
}

function getNotifications() {
    if (!currentUser) {
        window.location.href = '/jobseeker-login.html';
        return;
    }
    fetch('/api/notifications', {
        headers: { 'X-Username': currentUser.username }
    })
        .then(response => response.json())
        .then(notifications => {
            const notificationList = document.getElementById('notificationList');
            if (notificationList) {
                notificationList.innerHTML = '';
                if (notifications.length === 0) {
                    notificationList.innerHTML = '<p>No notifications</p>';
                } else {
                    notifications.forEach(notification => {
                        const div = document.createElement('div');
                        div.textContent = `${notification.message} (${notification.timestamp})`;
                        notificationList.appendChild(div);
                    });
                }
            }
        })
        .catch(error => {
            alert('Error fetching notifications: ' + error.message);
        });
}

function updateSkills() {
    if (!currentUser) {
        window.location.href = '/jobseeker-login.html';
        return;
    }
    const skills = document.getElementById('userSkills').value.split(',').map(s => s.trim()).filter(s => s);
    fetch('/api/user/skills', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'X-Username': currentUser.username
        },
        body: JSON.stringify({ skills })
    })
        .then(response => response.json())
        .then(data => {
            alert(data.message);
            if (data.success) {
                document.getElementById('skillsForm').reset();
            }
        })
        .catch(error => {
            alert('Error: ' + error.message);
        });
}