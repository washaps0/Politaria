const menuButton = document.querySelector('.menu-toggle');
const nav = document.querySelector('.nav');

if (menuButton && nav) {
  menuButton.addEventListener('click', () => {
    const isOpen = nav.classList.toggle('open');
    menuButton.setAttribute('aria-expanded', String(isOpen));
  });
}

// Use one reliable logo implementation on every page.
document.querySelectorAll('.brand-mark').forEach((node) => {
  if (node.tagName === 'IMG') {
    const logo = document.createElement('span');
    logo.className = 'brand-mark';
    logo.setAttribute('aria-hidden', 'true');
    node.replaceWith(logo);
  }
});

// Force the current favicon instead of an old cached JPG.
let favicon = document.querySelector('link[rel="icon"]');
if (!favicon) {
  favicon = document.createElement('link');
  favicon.rel = 'icon';
  document.head.appendChild(favicon);
}
favicon.type = 'image/png';
favicon.href = 'assets/images/politaria-logo-v3.png?v=3';

document.querySelectorAll('[data-copy]').forEach((button) => {
  button.addEventListener('click', async () => {
    const text = button.dataset.copy || '';
    try {
      await navigator.clipboard.writeText(text);
      const original = button.textContent;
      button.textContent = 'Скопировано';
      setTimeout(() => { button.textContent = original; }, 1400);
    } catch {
      button.textContent = text;
    }
  });
});

document.querySelectorAll('[data-year]').forEach((node) => {
  node.textContent = new Date().getFullYear();
});
