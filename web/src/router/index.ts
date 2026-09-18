import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/chat' },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { title: '对话' },
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/KnowledgeView.vue'),
    meta: { title: '知识问答' },
  },
  {
    path: '/store',
    name: 'Store',
    component: () => import('@/views/StoreView.vue'),
    meta: { title: '商店' },
  },
  {
    path: '/drama',
    name: 'Drama',
    component: () => import('@/views/DramaView.vue'),
    meta: { title: '短剧工坊' },
  },
  {
    path: '/media',
    name: 'Media',
    component: () => import('@/views/MediaView.vue'),
    meta: { title: '媒体工作台' },
  },
  {
    path: '/cms',
    name: 'Cms',
    component: () => import('@/views/CmsView.vue'),
    meta: { title: '内容中心' },
  },
  {
    path: '/cms/:slug',
    name: 'CmsDetail',
    component: () => import('@/views/CmsView.vue'),
    meta: { title: '内容详情' },
  },
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to, _from, next) => {
  if (to.meta.title) document.title = `${to.meta.title as string} - YuTong`
  next()
})

export default router
