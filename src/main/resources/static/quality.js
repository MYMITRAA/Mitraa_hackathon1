(() => {
  const main = document.querySelector('main');
  if (main) {
    if (!main.id) main.id = 'main-content';
    if (!document.querySelector('.skip-link')) {
      const skip = document.createElement('a');
      skip.className = 'skip-link';
      skip.href = `#${main.id}`;
      skip.textContent = 'Skip to main content';
      document.body.prepend(skip);
    }
  }

  document.querySelectorAll('a[target="_blank"]').forEach(link => {
    const rel = new Set((link.rel || '').split(/\s+/).filter(Boolean));
    rel.add('noopener');
    rel.add('noreferrer');
    link.rel = [...rel].join(' ');
  });

  const current = location.pathname.split('/').pop() || 'index.html';
  document.querySelectorAll('nav a[href], .sidebar a[href]').forEach(link => {
    const target = link.getAttribute('href')?.split(/[?#]/)[0];
    if (target === current || (current === 'index.html' && target === '/')) {
      link.setAttribute('aria-current', 'page');
    }
  });

  document.querySelectorAll('.form-status').forEach(status => {
    if (!status.hasAttribute('aria-live')) status.setAttribute('aria-live', 'polite');
    status.setAttribute('role', 'status');
  });
})();
