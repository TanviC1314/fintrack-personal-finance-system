import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Pagination } from './Pagination'

describe('Pagination', () => {
  it.each([
    [1, 'entry', '1 entry'],
    [73, 'entry', '73 entries'],
    [2, 'transaction', '2 transactions'],
  ])('describes %i %s correctly', (total, noun, expected) => {
    render(<Pagination page={0} totalPages={8} totalElements={total} noun={noun} onPageChange={vi.fn()} />)

    expect(screen.getByText(new RegExp(expected))).toBeInTheDocument()
  })

  it('disables the buttons at the first and last page and reports the requested page', async () => {
    const onPageChange = vi.fn()
    const { rerender } = render(<Pagination page={0} totalPages={3} totalElements={30} noun="entry" onPageChange={onPageChange} />)

    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }))
    expect(onPageChange).toHaveBeenCalledWith(1)

    rerender(<Pagination page={2} totalPages={3} totalElements={30} noun="entry" onPageChange={onPageChange} />)
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
  })

  it('renders nothing when there are no results', () => {
    const { container } = render(<Pagination page={0} totalPages={0} totalElements={0} noun="entry" onPageChange={vi.fn()} />)

    expect(container).toBeEmptyDOMElement()
  })
})
