(function () {
  'use strict';

  const NS = 'http://www.w3.org/2000/svg';
  const TAB_META = {
    31: ['兼容态势页', '保留旧版聚合页面兼容能力'],
    0: ['实时战报', '聚合今日战报、活跃玩家与实时事件'],
    1: ['排行中心', '对比玩家、同盟与势力表现'],
    7: ['玩家队伍', '检索玩家队伍与武将配置'],
    8: ['自定义积分', '查看可配置赛季积分与排名变化'],
    10: ['全部战报', '筛选并追踪最新交战记录'],
    12: ['城池地图', '查看城池归属与战场空间态势'],
    14: ['同盟成员', '管理成员资料与当前状态'],
    16: ['打城考勤', '记录打城参与和贡献情况'],
    17: ['同盟成员队伍', '汇总同盟成员队伍配置'],
    18: ['同盟势力', '对比同盟总势力与玩家个人势力'],
    23: ['武将阵容', '查询武将组合和队伍构成'],
    24: ['团数据', '汇总团队指标与成员表现'],
    25: ['战斗模拟', '配置双方阵容并推演战斗'],
    26: ['州郡分布', '观察州郡人数和势力分布'],
    29: ['古代中国地图', '浏览古代州郡展示图'],
    30: ['世界场景', '查看地图、行军、部队与协议读模型'],
    32: ['设置中心', '管理刷新、密度、动效与本地提醒偏好'],
    33: ['战场情报', '统一 WorldState 风险地图、地块详情与变化时间线'],
    34: ['阵容战法研究', '客户端配置事实、历史统计与模拟验证'],
    35: ['实时部队', '查看全部部队状态、实时位置与精确阵容证据']
  };

  const ICONS = {
    shield: ['M12 3 20 6v5c0 5-3.4 8.6-8 10-4.6-1.4-8-5-8-10V6l8-3Z', 'M9 12l2 2 4-5'],
    pulse: ['M3 12h4l2-5 4 10 2-5h6'],
    report: ['M6 3h9l3 3v15H6z', 'M14 3v4h4', 'M9 12h6', 'M9 16h6'],
    monitor: ['M4 5h16v11H4z', 'M8 20h8', 'M12 16v4', 'M7 11h2l1-3 3 6 1-3h3'],
    rank: ['M8 21v-6H4v6', 'M14 21V9h-4v12', 'M20 21V3h-4v18'],
    map: ['M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z', 'M9 3v15', 'M15 6v15'],
    users: ['M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2', 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8', 'M22 21v-2a4 4 0 0 0-3-3.87', 'M16 3.13a4 4 0 0 1 0 7.75'],
    calendar: ['M4 5h16v16H4z', 'M8 3v4', 'M16 3v4', 'M4 10h16', 'M8 14h2', 'M14 14h2', 'M8 18h2'],
    globe: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z', 'M2 12h20', 'M12 2a15 15 0 0 1 0 20', 'M12 2a15 15 0 0 0 0 20'],
    more: ['M5 12h.01', 'M12 12h.01', 'M19 12h.01'],
    menu: ['M4 7h16', 'M4 12h16', 'M4 17h16'],
    close: ['M6 6l12 12', 'M18 6 6 18'],
    panel: ['M4 4h16v16H4z', 'M9 4v16', 'M9 10h11']
  };

  const TAB_ICONS = {0: 'pulse', 1: 'rank', 10: 'report', 12: 'map', 14: 'users', 16: 'calendar', 22: 'pulse', 24: 'users', 27: 'monitor', 30: 'globe', 31: 'shield', 32: 'panel', 33: 'map', 34: 'report'};
  const NAV_GROUPS = [
    { label: 'INTELLIGENCE', tabs: [33, 35, 26] },
    { label: 'OPERATIONS', tabs: [25, 16] },
    { label: 'ORGANIZATION', tabs: [7, 17, 24] },
    { label: 'ANALYSIS', tabs: [8, 23, 34] },
    { label: 'SYSTEM', tabs: [32] }
  ];
  const OVERLAY_TARGETS = Object.freeze([
    ['body > header', 'hud-surface-overlay'],
    ['body > nav', 'hud-surface-overlay'],
    ['#cc-command-dialog .cc-command-shell', 'hud-surface-modal'],
    ['.hud-panel-glass', 'hud-surface-raised']
  ]);

  function icon(name, className) {
    const svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    if (className) svg.setAttribute('class', className);
    (ICONS[name] || ICONS.panel).forEach(function (d) {
      const path = document.createElementNS(NS, 'path');
      path.setAttribute('d', d);
      svg.appendChild(path);
    });
    return svg;
  }

  function parseTabIndex(button) {
    const match = (button.getAttribute('onclick') || '').match(/switchTab\((\d+)/);
    return match ? Number(match[1]) : -1;
  }

  function addBrand(nav) {
    const brand = document.createElement('div');
    brand.className = 'ds-brand';
    const mark = document.createElement('span');
    mark.className = 'ds-brand-mark';
    mark.appendChild(icon('shield'));
    const name = document.createElement('span');
    name.className = 'ds-brand-name';
    name.textContent = 'STZB Watcher';
    const meta = document.createElement('span');
    meta.className = 'ds-brand-meta';
    meta.textContent = 'BATTLE COMMAND';
    brand.append(mark, name, meta);
    nav.before(brand);
  }

  function enhanceNavigation(nav) {
    addBrand(nav);
    const buttons = Array.from(nav.querySelectorAll(':scope > button'));

    buttons.forEach(function (button) {
      const index = parseTabIndex(button);
      const meta = TAB_META[index];
      button.dataset.tabIndex = String(index);
      if (meta) {
        button.textContent = meta[0];
        button.setAttribute('aria-label', meta[0]);
      }

      const iconBox = document.createElement('span');
      iconBox.className = 'ds-nav-icon';
      iconBox.appendChild(icon(TAB_ICONS[index] || 'panel'));
      const text = document.createElement('span');
      text.className = 'ds-nav-text';
      text.textContent = button.textContent;
      button.replaceChildren(iconBox, text);
      if (button.classList.contains('active')) button.setAttribute('aria-current', 'page');
    });
  }

  function addNavigationGroups(nav) {
    const byTab = new Map(
      Array.from(nav.querySelectorAll('[data-tab-index]')).map(function (button) {
        return [Number(button.dataset.tabIndex), button];
      })
    );
    NAV_GROUPS.forEach(function (group) {
      const first = group.tabs.map(function (tab) { return byTab.get(tab); }).find(Boolean);
      if (!first) return;
      const label = document.createElement('div');
      label.className = 'ds-nav-group';
      label.textContent = group.label;
      first.before(label);
    });
  }

  function addMobileMenu(header, nav) {
    const toggle = document.createElement('button');
    toggle.type = 'button';
    toggle.className = 'ds-menu-toggle';
    toggle.setAttribute('aria-label', '打开主导航');
    toggle.setAttribute('aria-expanded', 'false');
    toggle.appendChild(icon('menu'));
    header.prepend(toggle);

    const backdrop = document.createElement('button');
    backdrop.type = 'button';
    backdrop.className = 'ds-nav-backdrop';
    backdrop.setAttribute('aria-label', '关闭主导航');
    nav.after(backdrop);

    const mobileHead = document.createElement('div');
    mobileHead.className = 'ds-nav-mobile-head';
    const mobileTitle = document.createElement('span');
    mobileTitle.textContent = 'STZB Watcher';
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'ds-nav-close';
    close.setAttribute('aria-label', '关闭主导航');
    close.appendChild(icon('close'));
    mobileHead.append(mobileTitle, close);
    nav.prepend(mobileHead);

    function setOpen(open) {
      document.body.classList.toggle('ds-nav-open', open);
      toggle.setAttribute('aria-expanded', String(open));
      toggle.setAttribute('aria-label', open ? '关闭主导航' : '打开主导航');
    }
    toggle.addEventListener('click', function () { setOpen(!document.body.classList.contains('ds-nav-open')); });
    close.addEventListener('click', function () { setOpen(false); toggle.focus(); });
    backdrop.addEventListener('click', function () { setOpen(false); });
    nav.addEventListener('click', function (event) { if (event.target.closest('[data-tab-index]')) setOpen(false); });
    document.addEventListener('keydown', function (event) { if (event.key === 'Escape') setOpen(false); });
  }

  function enhanceAccessibility() {
    const skip = document.createElement('a');
    skip.className = 'ds-skip-link';
    skip.href = '#dashboard-main';
    skip.textContent = '跳转到主内容';
    document.body.prepend(skip);

    document.querySelectorAll('.page').forEach(function (page) {
      page.setAttribute('role', 'region');
      page.setAttribute('aria-label', (TAB_META[Number(page.id.replace('tab', ''))] || ['数据工作台'])[0]);
      page.setAttribute('aria-hidden', String(!page.classList.contains('active')));
    });
    document.querySelectorAll('input, select, textarea').forEach(function (control) {
      if (control.getAttribute('aria-label') || control.getAttribute('aria-labelledby')) return;
      const explicit = control.id && document.querySelector('label[for="' + CSS.escape(control.id) + '"]');
      if (explicit) return;
      const fallback = control.getAttribute('placeholder') || control.getAttribute('title') ||
        (control.tagName === 'SELECT' && control.options[0] ? control.options[0].textContent : '') || '数据输入';
      control.setAttribute('aria-label', fallback.trim());
    });
    return skip;
  }

  function pageHeading(index) {
    const meta = TAB_META[index] || ['数据工作台', '查看当前业务数据与运行状态'];
    const heading = document.createElement('div');
    heading.className = 'ds-page-heading';
    const copy = document.createElement('div');
    copy.className = 'ds-page-heading-copy';
    const eyebrow = document.createElement('div');
    eyebrow.className = 'ds-page-eyebrow';
    eyebrow.textContent = 'STZB · COMMAND CENTER';
    const title = document.createElement('h2');
    title.textContent = meta[0];
    const description = document.createElement('p');
    description.textContent = meta[1];
    copy.append(eyebrow, title, description);
    const context = document.createElement('div');
    context.className = 'ds-page-context';
    context.textContent = '实时数据 · 自动更新';
    heading.append(copy, context);
    return heading;
  }

  function addPageHeadings() {
    document.querySelectorAll('.page[id^="tab"]').forEach(function (page) {
      if (page.querySelector('.hud-page-head, :scope > .ds-page-heading')) return;
      const index = Number(page.id.replace('tab', ''));
      page.prepend(pageHeading(index));
    });
  }

  function normalizeOverlaySurfaces() {
    OVERLAY_TARGETS.forEach(function (target) {
      document.querySelectorAll(target[0]).forEach(function (element) {
        element.classList.add(target[1]);
      });
    });
  }

  function syncActiveState(nav, skip) {
    const activePage = document.querySelector('.page.active[id^="tab"]');
    let activeIndex = Number(activePage?.id.replace('tab', '') || 33);
    document.querySelectorAll('.page').forEach(function (page) {
      page.setAttribute('aria-hidden', String(!page.classList.contains('active')));
    });
    nav.querySelectorAll('[data-tab-index]').forEach(function (button) {
      if (button.classList.contains('active')) {
        button.setAttribute('aria-current', 'page');
        activeIndex = Number(button.dataset.tabIndex);
      }
      else button.removeAttribute('aria-current');
    });
    if (skip) skip.href = '#dashboard-main';
    window.HudSystem?.setDomain(activeIndex);
    window.dispatchEvent(
      new CustomEvent('stzb:tab-changed', {detail: {tabId: activeIndex}})
    );
  }

  function replaceDecorativeSymbols() {
    const leadingSymbols = /^[\p{Extended_Pictographic}\uFE0F\u200D\s]+/u;
    document.querySelectorAll('.tbl-head h3, .btn, .ws-subtab').forEach(function (heading) {
      if (heading.querySelector('.ds-inline-icon')) return;
      const original = heading.textContent || '';
      const clean = original.replace(leadingSymbols, '').trim();
      if (!clean || clean === original.trim()) return;
      heading.textContent = clean;
      heading.prepend(icon(heading.matches('button, .btn') ? 'pulse' : 'panel', 'ds-inline-icon'));
    });
  }

  function init() {
    const nav = document.querySelector('body > nav');
    const header = document.querySelector('body > header');
    if (!nav || !header || nav.dataset.dsReady === 'true') return;
    nav.dataset.dsReady = 'true';
    enhanceNavigation(nav);
    addNavigationGroups(nav);
    addMobileMenu(header, nav);
    addPageHeadings();
    normalizeOverlaySurfaces();
    const skip = enhanceAccessibility();
    replaceDecorativeSymbols();
    syncActiveState(nav, skip);

    const observer = new MutationObserver(function () { syncActiveState(nav, skip); });
    nav.querySelectorAll('[data-tab-index]').forEach(function (button) {
      observer.observe(button, {attributes: true, attributeFilter: ['class']});
    });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
