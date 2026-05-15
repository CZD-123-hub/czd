import { use, init, type EChartsType, type EChartsCoreOption } from 'echarts/core'
import { GraphChart, HeatmapChart, RadarChart } from 'echarts/charts'
import { CalendarComponent, TooltipComponent, VisualMapComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([
  GraphChart,
  HeatmapChart,
  RadarChart,
  TooltipComponent,
  CalendarComponent,
  VisualMapComponent,
  CanvasRenderer,
])

export { init }
export type { EChartsType, EChartsCoreOption }
