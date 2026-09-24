import { useEffect, useRef } from 'react'
import * as echarts from 'echarts/core'

/**
 * Mounts an echarts instance onto an element and keeps it in step with its option
 * factory and its dependency.
 *
 * The instance is created once and never recreated: echarts tears down its canvas on
 * dispose, and rebuilding it on every render is what makes a chart flicker. A theme
 * change re-reads the tokens, so the option factory is refreshed and re-applied.
 */
export function useChart(createOption: () => echarts.EChartsCoreOption, dependency: unknown) {
  const element = useRef<HTMLDivElement>(null)
  const createOptionRef = useRef(createOption)
  const chartRef = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    createOptionRef.current = createOption
  }, [createOption])

  useEffect(() => {
    if (!element.current) return
    const chart = echarts.init(element.current)
    chartRef.current = chart
    const render = () => {
      chart.setOption(createOptionRef.current(), true)
      chart.resize()
    }
    render()
    const observer = new ResizeObserver(() => chart.resize())
    observer.observe(element.current)
    window.addEventListener('prelude-theme-change', render)
    return () => {
      window.removeEventListener('prelude-theme-change', render)
      observer.disconnect()
      chart.dispose()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    chartRef.current?.setOption(createOptionRef.current(), true)
  }, [dependency])
  return element
}
