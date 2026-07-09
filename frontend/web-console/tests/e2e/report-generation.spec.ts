import { expect, test } from '@playwright/test';

test.describe('智能报告生成 E2E', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/v1/enterprise-export-templates?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { items: [], page: 1, pageSize: 20, total: 0 }
        }
      });
    });
  });

  test('P0：全局导航按原型分组且不出现英文菜单项', async ({ page }) => {
    await page.route('**/api/v1/report-templates', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: [] } });
    });

    await page.goto('/reports/create');

    await expect(page.getByText('报告中心')).toBeVisible();
    await expect(page.getByText('管理中心')).toBeVisible();
    await expect(page.getByRole('menuitem', { name: '智能生成' })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: '模板填报' })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: '企业导出模板' })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: '审批委托' })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: '审批模板' })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: '组织管理' })).toBeVisible();
    await expect(page.getByText('Enterprise export templates')).toHaveCount(0);
    await expect(page.getByText('Approval delegates')).toHaveCount(0);
    await expect(page.getByText('Approval templates')).toHaveCount(0);
    await expect(page.getByText('Organization management')).toHaveCount(0);
  });

  test('P0：登录页使用独立认证布局且不展示业务侧边栏', async ({ page }) => {
    await page.goto('/login');

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: '智能报告生成系统' })).toBeVisible();
    await expect(page.getByRole('button', { name: '进入统一登录' })).toBeVisible();
    await expect(page.getByText('报告中心')).toHaveCount(0);
    await expect(page.getByRole('menuitem', { name: '智能生成' })).toHaveCount(0);
    await expect(page.getByRole('menuitem', { name: '组织管理' })).toHaveCount(0);
  });

  test('P0：本地预览点击统一登录进入系统而不是打开后端 JSON', async ({ page }) => {
    let devLoginCalled = false;
    await page.route('**/api/v1/auth/dev-login', async (route) => {
      devLoginCalled = true;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            accessToken: 'dev-jwt-token',
            userId: 1,
            displayName: '本地开发管理员',
            roles: ['system_admin'],
            permissions: ['report:create']
          }
        }
      });
    });
    await page.route('**/api/v1/report-templates', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: [] } });
    });
    await page.goto('/login');

    await page.getByRole('button', { name: '进入统一登录' }).click();

    await expect(page).toHaveURL(/\/reports\/create/);
    await expect.poll(() => devLoginCalled).toBe(true);
    await expect.poll(() => page.evaluate(() => window.localStorage.getItem('accessToken'))).toBe('dev-jwt-token');
    await expect(page.getByText('"code":401')).toHaveCount(0);
    await expect(page.getByText('请登录后继续操作')).toHaveCount(0);
  });

  test('P0：本地预览降级登录后业务接口 401 不闪回登录页', async ({ page }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));

    await page.route('**/api/v1/auth/dev-login', async (route) => {
      await route.fulfill({
        status: 401,
        json: { code: 401, message: '请登录后继续操作', data: null }
      });
    });
    await page.route('**/api/v1/report-templates', async (route) => {
      await route.fulfill({
        status: 401,
        json: { code: 401, message: '请登录后继续操作', data: null }
      });
    });

    await page.goto('/login');
    await page.getByRole('button', { name: '进入统一登录' }).click();

    await expect(page).toHaveURL(/\/reports\/create/);
    await page.waitForTimeout(500);
    await expect(page).toHaveURL(/\/reports\/create/);
    await expect.poll(() => page.evaluate(() => window.localStorage.getItem('authMode'))).toBe('local-preview');
    await expect(page.getByText('请登录后继续操作')).toHaveCount(0);
    expect(pageErrors).toEqual([]);
  });

  test('P0：报告生成页对齐原型三栏主流程并可完成演示生成闭环', async ({ page }) => {
    await page.route('**/api/v1/report-templates', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: [] } });
    });
    await page.route('**/api/v1/reports/generation-tasks', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: { taskId: 'task-prototype-001', status: 'outline_generated' } } });
    });

    await page.goto('/reports/create');

    await expect(page.getByText('AI 对话输入')).toBeVisible();
    await expect(page.getByText('报告预览')).toBeVisible();
    await expect(page.getByText('来源引用')).toBeVisible();
    await expect(page.getByText('导出报告')).toBeVisible();
    await expect(page.getByText('版本历史')).toBeVisible();

    await page.getByPlaceholder('描述你的报告需求...').fill('生成 2026Q1 集团经营分析报告，关注收入、成本和回款风险');
    await page.getByRole('button', { name: '发送生成需求' }).click();

    await expect(page.getByText('任务 task-prototype-001 已生成大纲（大纲已生成）')).toBeVisible();
    await expect(page.getByText('报告大纲预览')).toBeVisible();
    await page.getByRole('button', { name: '确认，开始生成' }).click();

    await expect(page.getByText('检索知识库')).toBeVisible();
    await expect(page.getByText('分析数据')).toBeVisible();
    await expect(page.getByText('生成报告', { exact: true })).toBeVisible();
    await expect(page.getByText('报告生成完成')).toBeVisible();
    await expect(page.getByRole('heading', { name: '2026Q1 集团经营分析报告' })).toBeVisible();
    await expect(page.getByText('2026Q1 经营数据汇总表')).toBeVisible();
  });

  test('REQ-REPORT-001：自然语言创建报告任务并进入大纲确认', async ({ page }) => {
    await page.route('**/api/v1/report-templates', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: [] } });
    });
    await page.route('**/api/v1/reports/generation-tasks', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: { taskId: 'task-001', status: 'outline_generated' } } });
    });

    await page.goto('/reports/create');
    await page.getByLabel(/报告主题|主题/).fill('生成本季度经营分析报告');
    await page.getByRole('button', { name: /生成|创建/ }).click();

    await expect(page.getByText('task-001 大纲已生成')).toBeVisible();
  });

  test('UC-02：模板填报按后端字段 schema 创建报告任务', async ({ page }) => {
    let templatePayload: Record<string, unknown> | null = null;
    await page.route('**/api/v1/report-templates', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: [
            {
              templateId: 'enterprise-quarterly',
              name: '企业季度经营分析报告',
              category: 'operations',
              version: 'v1',
              status: 'active',
              fields: [
                { fieldKey: 'period', label: '报告周期', type: 'text', required: true, options: [], defaultValue: '2026Q1', helpText: '例如 2026Q1' },
                { fieldKey: 'scope', label: '对象范围', type: 'text', required: true, options: [], defaultValue: '集团整体', helpText: '组织、区域或业务线' },
                { fieldKey: 'focus', label: '关注重点', type: 'textarea', required: true, options: [], defaultValue: '', helpText: '管理层最关心的问题' },
                { fieldKey: 'style', label: '报告风格', type: 'select', required: true, options: ['管理摘要', '经营分析'], defaultValue: '管理摘要', helpText: '输出语气' }
              ]
            }
          ]
        }
      });
    });
    await page.route('**/api/v1/reports/template-generation-tasks', async (route) => {
      templatePayload = await route.request().postDataJSON();
      await route.fulfill({ json: { code: 200, message: 'ok', data: { taskId: 'template-task-001', status: 'outline_ready' } } });
    });

    await page.goto('/reports/create');
    await page.getByRole('tab', { name: '模板填报' }).click();
    await page.getByLabel('对象范围').fill('华东区');
    await page.getByLabel('关注重点').fill('收入与回款风险');
    await page.getByTestId('template-field-style').click();
    await page.getByRole('option', { name: '经营分析' }).click();
    await page.getByRole('button', { name: '按模板生成大纲' }).click();

    await expect(page.getByText('template-task-001 大纲待确认')).toBeVisible();
    expect(templatePayload).toMatchObject({
      templateId: 'enterprise-quarterly',
      payload: {
        period: '2026Q1',
        scope: '华东区',
        focus: '收入与回款风险',
        style: '经营分析'
      }
    });
  });

  test('REQ-REPORT-002/REQ-AI-001：SSE 展示生成阶段和完成标记', async ({ page }) => {
    let confirmedOutlinePayload: Record<string, unknown> | null = null;
    await page.route('**/api/v1/reports/generation-tasks/task-001/outline', async (route) => {
      confirmedOutlinePayload = await route.request().postDataJSON();
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { taskId: 'task-001', confirmed: true, nextStage: 'retrieval' }
        }
      });
    });
    await page.route('**/api/v1/reports/generation-tasks/task-001/stream', async (route) => {
      await route.fulfill({
        contentType: 'text/event-stream',
        body: [
          'data: {"type":"stage","taskId":"task-001","content":"检索知识库","stage":"retrieval","references":[],"progress":0.3,"errorCode":null,"traceId":"trace-001"}\n\n',
          'data: {"type":"delta","taskId":"task-001","content":"第一段报告正文","stage":"writing","references":[],"progress":0.6,"errorCode":null,"traceId":"trace-001"}\n\n',
          'data: {"type":"done","taskId":"task-001","content":"完成","stage":"export","references":[],"progress":1,"errorCode":null,"traceId":"trace-001"}\n\n'
        ].join('')
      });
    });

    await page.goto('/reports/task-001/outline');
    await page.getByRole('button', { name: /确认|开始生成/ }).click();

    await expect(page.getByText('大纲已确认：task-001')).toBeVisible();
    await expect(page.getByText('下一阶段：retrieval')).toBeVisible();
    await expect(page.getByText('第一段报告正文')).toBeVisible();
    await expect(page.getByText('完成')).toBeVisible();
    expect(confirmedOutlinePayload).toMatchObject({
      outline: { sections: ['经营概览', '收入与成本分析', '风险与建议'] },
      confirmed: true
    });
  });

  test('REQ-REPORT-004：报告详情支持企业 Word、PDF 与 PPT 导出', async ({ page }) => {
    let exportPayload: Record<string, unknown> | null = null;
    let controlledDownloadRequested = false;
    await page.route('**/api/v1/reports/88', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            reportId: '88',
            title: '季度经营分析报告',
            status: 'completed',
            currentVersionId: '21',
            sections: [{ sectionId: 's1', heading: '经营概览', content: '收入保持增长。' }]
          }
        }
      });
    });
    await page.route('**/api/v1/reports/88/versions', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: [
            { versionId: '21', versionNo: 2, changeReason: 'generation_completed', current: true },
            { versionId: '20', versionNo: 1, changeReason: 'generation_completed', current: false }
          ]
        }
      });
    });
    await page.route('**/api/v1/reports/88/exports', async (route) => {
      exportPayload = await route.request().postDataJSON();
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            exportFileId: '7001',
            status: 'completed',
            format: 'pdf',
            fileName: 'report-88-v21.pdf',
            contentType: 'application/pdf',
            downloadUrl: '/api/v1/files/report-exports/7001/download-url'
          }
        }
      });
    });
    await page.route('**/api/v1/files/report-exports/7001/download-url', async (route) => {
      controlledDownloadRequested = true;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            downloadUrl: 'http://127.0.0.1:19000/mock-download/report-88-v21.pdf?token=short'
          }
        }
      });
    });
    await page.goto('/reports/88');

    await expect(page.getByRole('button', { name: /PDF/ })).toBeEnabled();
    await expect(page.getByRole('button', { name: /PPT/ })).toBeEnabled();
    await page.getByRole('button', { name: /PDF/ }).click();
    await page.getByLabel('封面标题').fill('Board Strategy Pack');
    await page.getByLabel('目录标题').fill('Report Outline');
    await page.getByLabel('章节标题前缀').fill('Section');
    await page.getByLabel('标题字号').fill('30');
    await page.getByLabel('正文字号').fill('22');
    await page.getByLabel('页眉字号').fill('16');
    await page.getByLabel('页脚字号').fill('12');
    await page.getByRole('button', { name: '导出文件' }).click();

    await expect(page.getByText('导出已完成：report-88-v21.pdf')).toBeVisible();
    await expect.poll(() => controlledDownloadRequested).toBe(true);
    await expect(page).toHaveURL(/\/reports\/88$/);
    await expect(page.getByRole('link', { name: '下载导出文件' })).toHaveAttribute(
      'href',
      'http://127.0.0.1:19000/mock-download/report-88-v21.pdf?token=short'
    );
    expect(exportPayload).toMatchObject({
      format: 'pdf',
      templateId: 'enterprise-board',
      brand: {
        companyName: '示例集团',
        logoObjectKey: 'branding/contoso-logo.png',
        header: '集团经营分析报告',
        footer: '由智能报告生成系统生成',
        fontFamily: 'Microsoft YaHei',
        primaryColor: '#1F4E79',
        layout: {
          coverTitle: 'Board Strategy Pack',
          tocTitle: 'Report Outline',
          bodyTitlePrefix: 'Section',
          titleFontSize: 30,
          bodyFontSize: 22,
          headerFontSize: 16,
          footerFontSize: 12
        }
      }
    });
  });

  test('P0：报告详情默认导出配置使用中文业务文案', async ({ page }) => {
    await page.route('**/api/v1/reports/88', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            reportId: '88',
            title: '季度经营分析报告',
            status: 'completed',
            currentVersionId: '21',
            sections: [{ sectionId: 's1', heading: '经营概览', content: '收入保持增长。' }]
          }
        }
      });
    });
    await page.route('**/api/v1/reports/88/versions', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: [] } });
    });
    await page.route('**/api/v1/enterprise-export-templates?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { items: [], page: 1, pageSize: 20, total: 0 }
        }
      });
    });

    await page.goto('/reports/88');

    await expect(page.getByLabel('模板编码')).toBeVisible();
    await expect(page.getByLabel('标识对象键')).toBeVisible();
    await expect(page.getByLabel('指派用户编号')).toBeVisible();
    await expect(page.getByRole('textbox', { name: '页眉', exact: true })).toHaveValue('集团经营分析报告');
    await expect(page.getByRole('textbox', { name: '页脚', exact: true })).toHaveValue('由智能报告生成系统生成');
    await expect(page.getByLabel('目录标题')).toHaveValue('目录');
    await expect(page.getByText('暂无版本数据')).toBeVisible();
    const visibleText = await page.locator('body').innerText();
    expect(visibleText).not.toMatch(/Confidential Board Report|Generated by Intelligent Report System|Table of Contents|Version |No Data|Logo 对象键|模板 ID|指派用户 ID/);
  });

  test('REQ-REPORT-004：报告详情可选择已治理企业导出模板并复用后端品牌快照', async ({ page }) => {
    let exportPayload: Record<string, unknown> | null = null;
    await page.route('**/api/v1/reports/88', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            reportId: '88',
            title: '季度经营分析报告',
            status: 'completed',
            currentVersionId: '21',
            sections: [{ sectionId: 's1', heading: '经营概览', content: '收入保持增长。' }]
          }
        }
      });
    });
    await page.route('**/api/v1/reports/88/versions', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: [] } });
    });
    await page.route('**/api/v1/enterprise-export-templates?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              {
                id: 901,
                templateId: 'managed-board',
                name: 'Board governed template',
                version: 'v3',
                status: 'active',
                brandSnapshot: {
                  templateId: 'managed-board',
                  templateVersion: 'v3',
                  format: 'pdf',
                  companyName: 'Managed Finance',
                  logoObjectKey: 'logos/managed.svg',
                  header: 'Managed Board Pack',
                  footer: 'Managed confidential',
                  fontFamily: 'Arial',
                  primaryColor: '#155E75',
                  layout: {
                    coverTitle: 'Governed Board Pack',
                    tocTitle: 'Governed Contents',
                    bodyTitlePrefix: 'Governed Section'
                  }
                }
              }
            ],
            page: 1,
            pageSize: 20,
            total: 1
          }
        }
      });
    });
    await page.route('**/api/v1/reports/88/exports', async (route) => {
      exportPayload = await route.request().postDataJSON();
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            exportFileId: '7002',
            status: 'completed',
            format: 'pdf',
            fileName: 'report-88-managed.pdf',
            contentType: 'application/pdf',
            brandSnapshot: {
              templateId: 'managed-board',
              templateVersion: 'v3',
              format: 'pdf'
            }
          }
        }
      });
    });
    await page.route('**/api/v1/files/report-exports/7002/download-url', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            downloadUrl: 'http://127.0.0.1:19000/mock-download/report-88-managed.pdf?token=short'
          }
        }
      });
    });

    await page.goto('/reports/88');
    await page.locator('select[aria-label="已治理企业模板"]').selectOption('managed-board');
    await expect(page.getByText('Managed Board Pack')).toBeVisible();
    await expect(page.getByText('版本 v3')).toBeVisible();
    await page.getByRole('button', { name: /PDF/ }).click();
    await page.getByRole('button', { name: '导出文件' }).click();

    await expect(page.getByText('导出已完成：report-88-managed.pdf')).toBeVisible();
    expect(exportPayload).toMatchObject({
      format: 'pdf',
      templateId: 'managed-board'
    });
    expect(exportPayload).not.toHaveProperty('brand');
  });

  test('REQ-REPORT-004：报告详情加载多页已治理企业导出模板', async ({ page }) => {
    await page.route('**/api/v1/reports/88', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            reportId: '88',
            title: '季度经营分析报告',
            status: 'completed',
            currentVersionId: '21',
            sections: [{ sectionId: 's1', heading: '经营概览', content: '收入保持增长。' }]
          }
        }
      });
    });
    await page.route('**/api/v1/reports/88/versions', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: [] } });
    });
    await page.route('**/api/v1/enterprise-export-templates?**', async (route) => {
      const url = new URL(route.request().url());
      const pageNo = url.searchParams.get('page');
      const item = pageNo === '2'
        ? {
            id: 902,
            templateId: 'managed-board-page-2',
            name: 'Board governed template page 2',
            version: 'v1',
            status: 'active',
            brandSnapshot: {
              templateId: 'managed-board-page-2',
              templateVersion: 'v1',
              format: 'pdf',
              companyName: 'Managed Finance',
              logoObjectKey: 'logos/managed-page-2.svg',
              header: 'Managed Board Pack Page 2',
              footer: 'Managed confidential',
              fontFamily: 'Arial',
              primaryColor: '#155E75',
              layout: { coverTitle: 'Governed Board Pack' }
            }
          }
        : {
            id: 901,
            templateId: 'managed-board-page-1',
            name: 'Board governed template page 1',
            version: 'v1',
            status: 'active',
            brandSnapshot: {
              templateId: 'managed-board-page-1',
              templateVersion: 'v1',
              format: 'pdf',
              companyName: 'Managed Finance',
              logoObjectKey: 'logos/managed-page-1.svg',
              header: 'Managed Board Pack Page 1',
              footer: 'Managed confidential',
              fontFamily: 'Arial',
              primaryColor: '#155E75',
              layout: { coverTitle: 'Governed Board Pack' }
            }
          };
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [item],
            page: Number(pageNo ?? '1'),
            pageSize: 1,
            total: 2
          }
        }
      });
    });

    await page.goto('/reports/88');

    await expect(page.locator('select[aria-label="已治理企业模板"] option[value="managed-board-page-2"]')).toHaveCount(1);
  });

  test('REQ-REPORT-003：报告详情可点击引用标记查看来源快照和质量评分', async ({ page }) => {
    let referenceCalled = false;
    await page.route('**/api/v1/reports/88', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            reportId: '88',
            title: '季度经营分析报告',
            status: 'completed',
            currentVersionId: '21',
            sections: [
              {
                sectionId: 's1',
                heading: '经营概览',
                content: '收入增长 12%，但应收账款风险上升。',
                citations: [
                  {
                    referenceId: 77,
                    sourceTitle: '华东销售数据集',
                    sourceType: 'knowledge_document',
                    snapshot: '收入同比增长 12%，应收账款周转天数升高。',
                    score: { credibility: 0.92, citationQuality: 0.88 },
                    anchor: { sectionNo: 1, heading: '经营概览', text: '收入增长 12%' }
                  }
                ]
              }
            ]
          }
        }
      });
    });
    await page.route('**/api/v1/reports/88/versions', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: [] } });
    });
    await page.route('**/api/v1/reports/88/references/77', async (route) => {
      referenceCalled = true;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            reportId: 88,
            referenceId: 77,
            sourceTitle: '华东销售数据集',
            sourceType: 'knowledge_document',
            snapshot: '收入同比增长 12%，应收账款周转天数升高。',
            score: { credibility: 0.92, citationQuality: 0.88 },
            anchor: { sectionNo: 1, heading: '经营概览', text: '收入增长 12%' }
          }
        }
      });
    });

    await page.goto('/reports/88');
    await page.getByRole('button', { name: '引用 77' }).click();

    await expect.poll(() => referenceCalled).toBe(true);
    await expect(page.getByText('华东销售数据集')).toBeVisible();
    await expect(page.getByText('收入同比增长 12%，应收账款周转天数升高。')).toBeVisible();
    await expect(page.getByText('可信度 92%')).toBeVisible();
    await expect(page.getByText('引用质量 88%')).toBeVisible();
    await expect(page.locator('.anchor-text').getByText('收入增长 12%', { exact: true })).toBeVisible();
  });

  test('REQ-REPORT-005：报告详情可查看版本差异并回滚历史版本', async ({ page }) => {
    let rollbackCalled = false;
    await page.route('**/api/v1/reports/88', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            reportId: '88',
            title: '季度经营分析报告',
            status: 'completed',
            currentVersionId: '21',
            sections: [{ sectionId: 's1', heading: '经营概览', content: '收入保持增长。' }]
          }
        }
      });
    });
    await page.route('**/api/v1/reports/88/versions', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: [
            { versionId: '21', versionNo: 2, changeReason: 'generation_completed', current: true },
            { versionId: '20', versionNo: 1, changeReason: 'generation_completed', current: false }
          ]
        }
      });
    });
    await page.route('**/api/v1/reports/88/versions/diff**', async (route) => {
      expect(route.request().url()).toContain('baseVersionId=20');
      expect(route.request().url()).toContain('targetVersionId=21');
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            reportId: '88',
            baseVersionId: '20',
            targetVersionId: '21',
            summary: { added: 1, removed: 0, modified: 1, unchanged: 0 },
            changes: [
              { changeType: 'modified', heading: '经营概览', baseContent: '收入增长 8%。', targetContent: '收入增长 12%。' },
              { changeType: 'added', heading: '后续计划', targetContent: '扩展重点客户。' }
            ]
          }
        }
      });
    });
    await page.route('**/api/v1/reports/88/versions/20/rollback', async (route) => {
      rollbackCalled = true;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { reportId: '88', sourceVersionId: '20', newVersionId: '22', currentVersionId: '22', changeReason: 'rollback' }
        }
      });
    });

    await page.goto('/reports/88');
    await page.getByRole('button', { name: '查看差异' }).click();

    await expect(page.getByText('版本差异')).toBeVisible();
    await expect(page.getByText('新增 1，删除 0，修改 1，未变 0')).toBeVisible();
    await expect(page.getByText('修改：经营概览')).toBeVisible();

    await page.getByRole('button', { name: '回滚版本 v1' }).click();
    await expect(page.getByText('已回滚并生成新版本：22')).toBeVisible();
    expect(rollbackCalled).toBe(true);
  });

  test('REQ-COLLAB-002：报告详情支持选中文本创建批注任务', async ({ page }) => {
    let annotationPayload: Record<string, any> | null = null;
    const sectionContent = '收入增长 12%，需要核对回款风险。';

    await page.route('**/api/v1/reports/88', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            reportId: '88',
            title: '季度经营分析报告',
            status: 'completed',
            currentVersionId: '21',
            sections: [{ sectionId: 'summary', heading: '经营概览', content: sectionContent }]
          }
        }
      });
    });
    await page.route('**/api/v1/reports/88/versions', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: [
            { versionId: '21', versionNo: 2, changeReason: 'generation_completed', current: true },
            { versionId: '20', versionNo: 1, changeReason: 'generation_completed', current: false }
          ]
        }
      });
    });
    await page.route('**/api/v1/reports/88/annotations', async (route) => {
      annotationPayload = await route.request().postDataJSON();
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            annotationId: 'anno-001',
            taskId: 'task-collab-001',
            notificationId: 'notice-001',
            status: 'open'
          }
        }
      });
    });

    await page.goto('/reports/88');
    await page.locator('[data-section-id="summary"]').waitFor();
    await page.evaluate(() => {
      const paragraph = document.querySelector('[data-section-id="summary"] [data-section-content]');
      const textNode = paragraph?.firstChild;
      if (!paragraph || !textNode) throw new Error('section paragraph not found');
      const range = document.createRange();
      range.setStart(textNode, 13);
      range.setEnd(textNode, 17);
      const selection = window.getSelection();
      selection?.removeAllRanges();
      selection?.addRange(range);
      paragraph.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    });

    await expect(page.getByText('已选中：回款风险')).toBeVisible();
    await page.getByLabel('批注意见').fill('请财务同事核对回款风险');
    await page.getByLabel('指派用户编号').fill('user-finance');
    await page.getByRole('button', { name: '提交批注任务' }).click();

    await expect(page.getByText('批注已提交，任务：task-collab-001')).toBeVisible();
    expect(annotationPayload).toMatchObject({
      content: '请财务同事核对回款风险',
      assigneeUserId: 'user-finance',
      anchor: {
        sectionId: 'summary',
        startOffset: sectionContent.indexOf('回款风险'),
        endOffset: sectionContent.indexOf('回款风险') + '回款风险'.length,
        selectedText: '回款风险'
      }
    });
  });

  test('REQ-COLLAB-001：报告详情支持创建、复制和撤销分享链接', async ({ page, context }) => {
    let sharePayload: Record<string, unknown> | null = null;
    let revokeCalled = false;
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.route('**/api/v1/reports/88', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            reportId: '88',
            title: '季度经营分析报告',
            status: 'completed',
            currentVersionId: '21',
            sections: [{ sectionId: 'summary', heading: '经营概览', content: '收入保持增长。' }]
          }
        }
      });
    });
    await page.route('**/api/v1/reports/88/versions', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: [] } });
    });
    await page.route('**/api/v1/reports/88/share-links', async (route) => {
      sharePayload = await route.request().postDataJSON();
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            shareLinkId: 501,
            reportId: '88',
            shareToken: 'share-token-501',
            shareUrl: '/share/share-token-501',
            status: 'active',
            passwordRequired: true,
            allowDownload: true,
            expiresAt: '2026-06-27T08:00:00Z'
          }
        }
      });
    });
    await page.route('**/api/v1/share-links/share-token-501/revoke', async (route) => {
      revokeCalled = true;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            shareLinkId: 501,
            reportId: '88',
            shareToken: 'share-token-501',
            shareUrl: '/share/share-token-501',
            status: 'revoked',
            passwordRequired: true,
            allowDownload: true
          }
        }
      });
    });

    await page.goto('/reports/88');
    await page.getByLabel('分享密码').fill('ExternalPass#1');
    await page.getByTestId('share-max-access-count').getByRole('spinbutton').fill('1');
    await page.getByLabel('允许访问邮箱').fill('external@example.com');
    await page.getByLabel('允许访问域名').fill('partner.com');
    await page.getByTestId('share-allow-download').click();
    await page.getByTestId('share-single-use').click();
    await page.getByLabel('允许下载格式 Markdown').check();
    await page.getByLabel('分享有效期').fill('2026-06-27T08:00');
    await page.getByRole('button', { name: '创建分享链接' }).click();

    await expect(page.getByText('/share/share-token-501')).toBeVisible();
    await expect(page.getByText('状态：生效中')).toBeVisible();
    expect(sharePayload).toMatchObject({
      password: 'ExternalPass#1',
      allowDownload: true,
      allowedDownloadFormats: ['markdown'],
      maxAccessCount: 1,
      allowedVisitors: ['external@example.com'],
      allowedVisitorDomains: ['partner.com'],
      singleUse: true,
      expiresAt: '2026-06-27T00:00:00.000Z'
    });

    await page.getByRole('button', { name: '复制分享链接' }).click();
    await expect(page.getByText('分享链接已复制')).toBeVisible();
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toContain('/share/share-token-501');

    await page.getByRole('button', { name: '撤销分享链接' }).click();

    await expect.poll(() => revokeCalled).toBe(true);
    await expect(page.getByText('状态：已撤销')).toBeVisible();
    await expect(page.getByText('分享链接已撤销')).toBeVisible();
  });
});
