'use strict';
const $ = id => document.getElementById(id);
const subscription = new URL('tvbox.json', location.href).href;
$('subscription').textContent = subscription;
$('copy').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText(subscription); $('copy-status').textContent = '已复制，可粘贴到影视仓。'; }
  catch { $('copy-status').textContent = '请长按上方地址手动复制。'; }
});
let data, topic = '', page = 1, count = 0;
const size = 24;
function element(tag, cls, text) { const el = document.createElement(tag); if (cls) el.className = cls; if (text !== undefined) el.textContent = text; return el; }
function yearMatches(year, selected) {
  if (!selected || year === selected) return true;
  const parts = selected.split('-').map(Number);
  return parts.length === 2 && Number(year) >= Math.min(...parts) && Number(year) <= Math.max(...parts);
}
function render() {
  if (!data) return;
  const query = $('search').value.trim().toLocaleLowerCase(), year = $('year').value, series = $('series').value, sort = $('sort').value;
  const rows = data.videos.filter(v => (!topic || v.topics.includes(topic)) && yearMatches(v.year, year) && (!series || v.series.includes(series)) && v.title.toLocaleLowerCase().includes(query));
  rows.sort((a,b) => b[sort]-a[sort] || a.id.localeCompare(b.id));
  count = rows.length; page = Math.max(1, Math.min(page, Math.ceil(count/size) || 1));
  $('status').textContent = `${count.toLocaleString()} 条结果 / ${data.count.toLocaleString()} 条视频 · 更新 ${new Date(data.generated_at).toLocaleString('zh-CN')}`;
  $('videos').replaceChildren();
  for (const v of rows.slice((page-1)*size,page*size)) {
    const card = element('a','card'); card.href = 'https://dl.ccf.org.cn/video/videoDetail.html?id='+encodeURIComponent(v.id); card.target = '_blank'; card.rel = 'noopener noreferrer';
    const img = element('img'); img.alt = ''; img.loading = 'lazy';
    try { const u = new URL(v.cover); if (u.protocol === 'https:' && (u.hostname === 'ccf.org.cn' || u.hostname.endsWith('.ccf.org.cn'))) img.src = u.href; } catch {}
    const body = element('div','card-body'), meta = element('div','meta'); meta.append(element('span','',v.access || '权限以官网为准'),element('span','',`${v.year} · ${v.views} 浏览`));
    const names = data.topics.filter(t=>v.topics.includes(t.id)).map(t=>t.name);
    body.append(meta,element('h3','',v.title),element('div','tags',names.join(' / ') || 'CCF视频'));
    card.title = Object.values(v.matches || {}).flat().join('、'); card.append(img,body); $('videos').append(card);
  }
  if (!count) $('videos').append(element('p','empty','未找到匹配视频，试试其他关键词或筛选条件。'));
  $('page').textContent = `${page} / ${Math.ceil(count/size) || 1}`;
  $('previous').disabled = page <= 1; $('next').disabled = page*size >= count;
}
for (const id of ['search','year','series','sort']) $(id).addEventListener(id === 'search' ? 'input':'change',()=>{page=1;render();});
$('previous').addEventListener('click',()=>{page--;render();}); $('next').addEventListener('click',()=>{page++;render();});
fetch('catalog.json').then(r=>{if(!r.ok)throw new Error();return r.json();}).then(result=>{
  if(result.schema_version!==1 || !result.complete || !Array.isArray(result.videos)) throw new Error();
  data=result;
  for(const [id,key] of [['year','dateYears'],['series','meetingSeries']]) for(const value of data.conditions[key]) { const option=element('option','',value);option.value=value;$(id).append(option); }
  for(const t of [{id:'',name:'全部方向'},...data.topics]) {const button=element('button','',t.name);button.type='button';button.setAttribute('aria-pressed',String(t.id===topic));button.addEventListener('click',()=>{topic=t.id;page=1;for(const b of $('topics').children)b.setAttribute('aria-pressed',String(b===button));render();});$('topics').append(button);}
  render();
}).catch(()=>{$('status').textContent='目录加载失败，请稍后刷新或查看 Actions 构建状态。';$('videos').append(element('p','empty','目录暂不可用。影视仓中的 CCF视频和搜索仍可直接请求原站。'));});
