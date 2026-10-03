import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'

type ApiStatus = 'checking' | 'online' | 'offline'
type AuthMode = 'login' | 'register'

type AuthUser = {
  id: string
  email: string
  fullName: string
  role: string
}

type CsrfResponse = {
  token: string
  headerName: string
}

type Product = {
  id: string
  sku: string
  name: string
  description: string | null
  category: string
  unit: string
  price: number
  currencyCode: string
  active: boolean
  stockQuantity: number
}

type CartLine = {
  productId: string
  sku: string
  productName: string
  unit: string
  currencyCode: string
  unitPrice: number
  quantity: number
  availableQuantity: number
  active: boolean
  lineTotal: number
}

type Cart = {
  items: CartLine[]
  currencyCode: string | null
  totalQuantity: number
  subtotal: number
}

type OrderLine = {
  productId: string
  sku: string
  productName: string
  unit: string
  quantity: number
  unitPrice: number
  lineTotal: number
}

type Order = {
  id: string
  accountId: string
  customerEmail: string
  status: 'PLACED' | 'PROCESSING' | 'COMPLETED' | 'CANCELLED'
  currencyCode: string
  totalAmount: number
  placedAt: string
  items: OrderLine[]
}

type ProductPage = {
  content: Product[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

type ProductDraft = {
  sku: string
  name: string
  description: string
  category: string
  unit: string
  price: string
  currencyCode: string
}

const emptyProductDraft: ProductDraft = {
  sku: '',
  name: '',
  description: '',
  category: '',
  unit: 'case',
  price: '',
  currencyCode: 'LKR',
}

const emptyCart: Cart = { items: [], currencyCode: null, totalQuantity: 0, subtotal: 0 }

async function readResponse<T>(response: Response): Promise<T> {
  const body = await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(body?.detail ?? body?.message ?? 'The request could not be completed.')
  }
  return body as T
}

async function loadCsrf(): Promise<CsrfResponse> {
  const response = await fetch('/api/auth/csrf')
  return readResponse<CsrfResponse>(response)
}

export default function App() {
  const [apiStatus, setApiStatus] = useState<ApiStatus>('checking')
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null)
  const [authMode, setAuthMode] = useState<AuthMode>('login')
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [authError, setAuthError] = useState('')
  const [authMessage, setAuthMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [products, setProducts] = useState<Product[]>([])
  const [categories, setCategories] = useState<string[]>([])
  const [catalogQuery, setCatalogQuery] = useState('')
  const [selectedCategory, setSelectedCategory] = useState('')
  const [catalogPage, setCatalogPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [catalogLoading, setCatalogLoading] = useState(false)
  const [catalogError, setCatalogError] = useState('')
  const [catalogRefresh, setCatalogRefresh] = useState(0)
  const [showProductForm, setShowProductForm] = useState(false)
  const [editingProductId, setEditingProductId] = useState<string | null>(null)
  const [productDraft, setProductDraft] = useState<ProductDraft>(emptyProductDraft)
  const [productSaving, setProductSaving] = useState(false)
  const [cart, setCart] = useState<Cart>(emptyCart)
  const [cartError, setCartError] = useState('')
  const [cartLoading, setCartLoading] = useState(false)
  const [checkoutLoading, setCheckoutLoading] = useState(false)
  const [orders, setOrders] = useState<Order[]>([])
  const [orderError, setOrderError] = useState('')
  const [stockDrafts, setStockDrafts] = useState<Record<string, string>>({})
  const checkoutKey = useRef<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    fetch('/actuator/health', { signal: controller.signal })
      .then((response) => {
        if (!response.ok) throw new Error('Health check failed')
        return response.json() as Promise<{ status?: string }>
      })
      .then((health) => setApiStatus(health.status === 'UP' ? 'online' : 'offline'))
      .catch(() => {
        if (!controller.signal.aborted) setApiStatus('offline')
      })

    fetch('/api/auth/me', { signal: controller.signal })
      .then((response) => {
        if (response.status === 401) return null
        if (!response.ok) throw new Error('Could not restore the account session.')
        return response.json() as Promise<AuthUser>
      })
      .then((user) => {
        if (!controller.signal.aborted) setCurrentUser(user)
      })
      .catch(() => {
        if (!controller.signal.aborted) setCurrentUser(null)
      })

    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!currentUser) {
      setProducts([])
      setCategories([])
      return
    }

    const controller = new AbortController()
    const parameters = new URLSearchParams({ page: String(catalogPage), size: '12' })
    if (catalogQuery.trim()) parameters.set('q', catalogQuery.trim())
    if (selectedCategory) parameters.set('category', selectedCategory)
    const productsPath = currentUser.role === 'ADMIN' ? '/api/admin/products' : '/api/products'

    setCatalogLoading(true)
    setCatalogError('')
    fetch(`${productsPath}?${parameters}`, { signal: controller.signal })
      .then(readResponse<ProductPage>)
      .then((result) => {
        setProducts(result.content)
        setTotalPages(result.totalPages)
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setCatalogError(error instanceof Error ? error.message : 'Could not load products.')
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setCatalogLoading(false)
      })

    fetch('/api/products/categories', { signal: controller.signal })
      .then(readResponse<string[]>)
      .then(setCategories)
      .catch(() => {
        if (!controller.signal.aborted) setCategories([])
      })

    return () => controller.abort()
  }, [currentUser, catalogPage, catalogQuery, selectedCategory, catalogRefresh])

  useEffect(() => {
    if (!currentUser) {
      setCart(emptyCart)
      setOrders([])
      setCartError('')
      setOrderError('')
      return
    }

    const controller = new AbortController()
    const ordersPath = currentUser.role === 'ADMIN' ? '/api/admin/orders' : '/api/orders'
    setOrderError('')

    fetch(ordersPath, { signal: controller.signal })
      .then(readResponse<Order[]>)
      .then((result) => setOrders(result))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setOrderError(error instanceof Error ? error.message : 'Could not load orders.')
        }
      })

    if (currentUser.role === 'CUSTOMER') {
      setCartLoading(true)
      fetch('/api/cart', { signal: controller.signal })
        .then(readResponse<Cart>)
        .then(setCart)
        .catch((error: unknown) => {
          if (!controller.signal.aborted) {
            setCartError(error instanceof Error ? error.message : 'Could not load your cart.')
          }
        })
        .finally(() => {
          if (!controller.signal.aborted) setCartLoading(false)
        })
    } else {
      setCart(emptyCart)
    }

    return () => controller.abort()
  }, [currentUser])

  const statusLabel = {
    checking: 'Checking API',
    online: 'API connected',
    offline: 'API not connected',
  }[apiStatus]

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAuthError('')
    setAuthMessage('')
    setIsSubmitting(true)

    try {
      const csrf = await loadCsrf()
      const requestBody = authMode === 'register'
        ? { fullName, email, password }
        : { email, password }
      const response = await fetch(`/api/auth/${authMode === 'register' ? 'register' : 'login'}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify(requestBody),
      })
      const user = await readResponse<AuthUser>(response)

      if (authMode === 'register') {
        setAuthMode('login')
        setPassword('')
        setAuthMessage('Your account is ready. Sign in to continue.')
      } else {
        setCurrentUser(user)
        setPassword('')
        setAuthMessage('You are signed in.')
      }
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : 'The request could not be completed.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function signOut() {
    setAuthError('')
    setAuthMessage('')
    setIsSubmitting(true)

    try {
      const csrf = await loadCsrf()
      const response = await fetch('/api/auth/logout', {
        method: 'POST',
        headers: { [csrf.headerName]: csrf.token },
      })
      if (!response.ok) throw new Error('Could not sign out. Please try again.')
      setCurrentUser(null)
      setAuthMessage('You are signed out.')
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : 'Could not sign out.')
    } finally {
      setIsSubmitting(false)
    }
  }

  function changeAuthMode(mode: AuthMode) {
    setAuthMode(mode)
    setAuthError('')
    setAuthMessage('')
  }

  function editProduct(product?: Product) {
    setEditingProductId(product?.id ?? null)
    setProductDraft(product ? {
      sku: product.sku,
      name: product.name,
      description: product.description ?? '',
      category: product.category,
      unit: product.unit,
      price: String(product.price),
      currencyCode: product.currencyCode,
    } : emptyProductDraft)
    setCatalogError('')
    setShowProductForm(true)
  }

  async function saveProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setProductSaving(true)
    setCatalogError('')
    try {
      const csrf = await loadCsrf()
      const response = await fetch(editingProductId
        ? `/api/admin/products/${editingProductId}`
        : '/api/admin/products', {
        method: editingProductId ? 'PUT' : 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrf.headerName]: csrf.token,
        },
        body: JSON.stringify({ ...productDraft, price: Number(productDraft.price) }),
      })
      await readResponse<Product>(response)
      setShowProductForm(false)
      setCatalogRefresh((value) => value + 1)
    } catch (error) {
      setCatalogError(error instanceof Error ? error.message : 'Could not save the product.')
    } finally {
      setProductSaving(false)
    }
  }

  async function changeProductStatus(product: Product) {
    setCatalogError('')
    try {
      const csrf = await loadCsrf()
      const response = await fetch(`/api/admin/products/${product.id}/active?active=${!product.active}`, {
        method: 'PATCH',
        headers: { [csrf.headerName]: csrf.token },
      })
      await readResponse<null>(response)
      setCatalogRefresh((value) => value + 1)
    } catch (error) {
      setCatalogError(error instanceof Error ? error.message : 'Could not update the product.')
    }
  }

  async function addToCart(product: Product) {
    setCartError('')
    try {
      const csrf = await loadCsrf()
      const response = await fetch('/api/cart/items', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ productId: product.id, quantity: 1 }),
      })
      setCart(await readResponse<Cart>(response))
    } catch (error) {
      setCartError(error instanceof Error ? error.message : 'Could not add this product to your cart.')
    }
  }

  async function setCartItemQuantity(productId: string, quantity: number) {
    setCartError('')
    try {
      const csrf = await loadCsrf()
      const response = await fetch(`/api/cart/items/${productId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ quantity }),
      })
      setCart(await readResponse<Cart>(response))
    } catch (error) {
      setCartError(error instanceof Error ? error.message : 'Could not update your cart.')
    }
  }

  async function removeCartItem(productId: string) {
    setCartError('')
    try {
      const csrf = await loadCsrf()
      const response = await fetch(`/api/cart/items/${productId}`, {
        method: 'DELETE',
        headers: { [csrf.headerName]: csrf.token },
      })
      setCart(await readResponse<Cart>(response))
    } catch (error) {
      setCartError(error instanceof Error ? error.message : 'Could not remove this product.')
    }
  }

  async function checkout() {
    setCheckoutLoading(true)
    setCartError('')
    try {
      const csrf = await loadCsrf()
      checkoutKey.current ??= crypto.randomUUID()
      const response = await fetch('/api/orders/checkout', {
        method: 'POST',
        headers: {
          [csrf.headerName]: csrf.token,
          'Idempotency-Key': checkoutKey.current,
        },
      })
      const order = await readResponse<Order>(response)
      checkoutKey.current = null
      setCart(emptyCart)
      setOrders((current) => [order, ...current.filter((existing) => existing.id !== order.id)])
      setAuthMessage(`Order placed: ${order.id.slice(0, 8)}.`)
      setCatalogRefresh((value) => value + 1)
    } catch (error) {
      setCartError(error instanceof Error ? error.message : 'Could not place your order.')
    } finally {
      setCheckoutLoading(false)
    }
  }

  async function updateStock(product: Product, event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setCatalogError('')
    try {
      const csrf = await loadCsrf()
      const response = await fetch(`/api/admin/products/${product.id}/stock`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ quantity: Number(stockDrafts[product.id] ?? product.stockQuantity) }),
      })
      await readResponse<Product>(response)
      setCatalogRefresh((value) => value + 1)
    } catch (error) {
      setCatalogError(error instanceof Error ? error.message : 'Could not update stock.')
    }
  }

  async function updateOrderStatus(orderId: string, status: Order['status']) {
    setOrderError('')
    try {
      const csrf = await loadCsrf()
      const response = await fetch(`/api/admin/orders/${orderId}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        body: JSON.stringify({ status }),
      })
      const updated = await readResponse<Order>(response)
      setOrders((current) => current.map((order) => order.id === updated.id ? updated : order))
      setCatalogRefresh((value) => value + 1)
    } catch (error) {
      setOrderError(error instanceof Error ? error.message : 'Could not update this order.')
    }
  }

  function formatProductPrice(product: Product) {
    try {
      return new Intl.NumberFormat('en-LK', {
        style: 'currency',
        currency: product.currencyCode,
      }).format(product.price)
    } catch {
      return `${product.currencyCode} ${product.price.toFixed(2)}`
    }
  }

  function formatAmount(amount: number, currencyCode: string) {
    try {
      return new Intl.NumberFormat('en-LK', { style: 'currency', currency: currencyCode }).format(amount)
    } catch {
      return `${currencyCode} ${amount.toFixed(2)}`
    }
  }

  return (
    <main className="page-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="SupplyCart home">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>SupplyCart</span>
        </a>
        <div className={`api-status api-status--${apiStatus}`} role="status" aria-live="polite">
          <span className="status-dot" aria-hidden="true" />
          {statusLabel}
        </div>
      </header>

      <section className="welcome-card" aria-labelledby="welcome-title">
        <div className="welcome-copy">
          <p className="eyebrow">A better way to restock</p>
          <h1 id="welcome-title">Everything your kitchen needs, in one place.</h1>
          <p className="intro">
            SupplyCart is a foodservice ordering workspace for restaurants and the people
            who keep them running.
          </p>
          <div className="phase-note">
            <span className="phase-number">04</span>
            <span><strong>Ordering</strong><br />Browse, stock and checkout flows are ready.</span>
          </div>
        </div>
        <div className="welcome-art" aria-hidden="true">
          <div className="sun" />
          <div className="crate crate--back"><span /><span /><span /></div>
          <div className="crate crate--front"><span /><span /><span /></div>
          <div className="leaf leaf--one" />
          <div className="leaf leaf--two" />
          <div className="art-caption">GOOD FOOD<br />STARTS HERE</div>
        </div>
      </section>

      <section className="account-panel" aria-labelledby="account-title">
        {currentUser ? (
          <div className="account-signed-in">
            <div>
              <p className="eyebrow">Your account</p>
              <h2 id="account-title">Welcome, {currentUser.fullName}</h2>
              <p className="account-detail">{currentUser.email}</p>
              <span className="role-badge">{currentUser.role.toLowerCase()}</span>
            </div>
            <button className="secondary-button" type="button" onClick={signOut} disabled={isSubmitting}>
              {isSubmitting ? 'Signing out…' : 'Sign out'}
            </button>
          </div>
        ) : (
          <>
            <div className="account-heading">
              <p className="eyebrow">Customer account</p>
              <h2 id="account-title">{authMode === 'login' ? 'Welcome back' : 'Create your account'}</h2>
              <p>Sign in to your workspace or create a customer account.</p>
            </div>
            <div className="auth-tabs" role="group" aria-label="Account action">
              <button type="button" aria-pressed={authMode === 'login'} onClick={() => changeAuthMode('login')}>
                Sign in
              </button>
              <button type="button" aria-pressed={authMode === 'register'} onClick={() => changeAuthMode('register')}>
                Create account
              </button>
            </div>
            <form className="auth-form" onSubmit={submitAuth}>
              {authMode === 'register' && (
                <label>
                  Full name
                  <input
                    autoComplete="name"
                    maxLength={120}
                    required
                    value={fullName}
                    onChange={(event) => setFullName(event.target.value)}
                  />
                </label>
              )}
              <label>
                Email address
                <input
                  autoComplete="email"
                  maxLength={254}
                  required
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </label>
              <label>
                Password
                <input
                  autoComplete={authMode === 'register' ? 'new-password' : 'current-password'}
                  maxLength={72}
                  minLength={authMode === 'register' ? 12 : undefined}
                  required
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
                {authMode === 'register' && <span className="field-hint">Use at least 12 characters.</span>}
              </label>
              {authError && <p className="form-message form-message--error" role="alert">{authError}</p>}
              {authMessage && <p className="form-message form-message--success" role="status">{authMessage}</p>}
              <button className="primary-button" type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Please wait…' : authMode === 'login' ? 'Sign in' : 'Create customer account'}
              </button>
            </form>
          </>
        )}
        {currentUser && authMessage && <p className="form-message form-message--success" role="status">{authMessage}</p>}
        {currentUser && authError && <p className="form-message form-message--error" role="alert">{authError}</p>}
      </section>

      {currentUser ? (
        <section className="catalog-section" aria-labelledby="catalog-title">
          <div className="catalog-heading">
            <div>
              <p className="eyebrow">SupplyCart selection</p>
              <h2 id="catalog-title">Product catalog</h2>
              <p>Find the ingredients and supplies your kitchen needs.</p>
            </div>
            {currentUser.role === 'ADMIN' && (
              <button className="primary-button" type="button" onClick={() => editProduct()}>
                Add product
              </button>
            )}
          </div>

          <div className="catalog-filters">
            <label className="catalog-search">
              Search products
              <input
                type="search"
                maxLength={100}
                placeholder="Name, SKU or category"
                value={catalogQuery}
                onChange={(event) => {
                  setCatalogQuery(event.target.value)
                  setCatalogPage(0)
                }}
              />
            </label>
            <label>
              Category
              <select
                value={selectedCategory}
                onChange={(event) => {
                  setSelectedCategory(event.target.value)
                  setCatalogPage(0)
                }}
              >
                <option value="">All categories</option>
                {categories.map((category) => <option key={category} value={category}>{category}</option>)}
              </select>
            </label>
            <span className="catalog-count">{products.length} shown · page {catalogPage + 1} of {Math.max(totalPages, 1)}</span>
          </div>

          {catalogError && <p className="form-message form-message--error" role="alert">{catalogError}</p>}

          {showProductForm && currentUser.role === 'ADMIN' && (
            <form className="product-form" onSubmit={saveProduct}>
              <div className="product-form-heading">
                <h3>{editingProductId ? 'Edit product' : 'Add a product'}</h3>
                <button className="text-button" type="button" onClick={() => setShowProductForm(false)}>Cancel</button>
              </div>
              <label>SKU<input required maxLength={64} value={productDraft.sku} onChange={(event) => setProductDraft({ ...productDraft, sku: event.target.value })} /></label>
              <label>Product name<input required maxLength={160} value={productDraft.name} onChange={(event) => setProductDraft({ ...productDraft, name: event.target.value })} /></label>
              <label>Category<input required maxLength={80} value={productDraft.category} onChange={(event) => setProductDraft({ ...productDraft, category: event.target.value })} /></label>
              <label>Unit (e.g. case, kg)<input required maxLength={32} value={productDraft.unit} onChange={(event) => setProductDraft({ ...productDraft, unit: event.target.value })} /></label>
              <label>Price<input required type="number" min="0.01" step="0.01" value={productDraft.price} onChange={(event) => setProductDraft({ ...productDraft, price: event.target.value })} /></label>
              <label>Currency code<input required minLength={3} maxLength={3} pattern="[A-Za-z]{3}" value={productDraft.currencyCode} onChange={(event) => setProductDraft({ ...productDraft, currencyCode: event.target.value.toUpperCase() })} /></label>
              <label className="product-description">Description<textarea maxLength={2000} rows={3} value={productDraft.description} onChange={(event) => setProductDraft({ ...productDraft, description: event.target.value })} /></label>
              <button className="primary-button" type="submit" disabled={productSaving}>{productSaving ? 'Saving…' : 'Save product'}</button>
            </form>
          )}

          {catalogLoading ? (
            <p className="catalog-empty" role="status">Loading products…</p>
          ) : products.length === 0 ? (
            <p className="catalog-empty">{currentUser.role === 'ADMIN' ? 'No products yet. Add the first catalog item above.' : 'No products match this search yet.'}</p>
          ) : (
            <div className="product-grid">
              {products.map((product) => (
                <article className={`product-card${product.active ? '' : ' product-card--inactive'}`} key={product.id}>
                  <div className="product-art" aria-hidden="true">{product.category.slice(0, 1).toUpperCase()}</div>
                  <div className="product-card-content">
                    <div className="product-card-meta"><span>{product.category}</span><span>{product.sku}</span></div>
                    <h3>{product.name}</h3>
                    {product.description && <p>{product.description}</p>}
                    <div className="product-card-bottom">
                      <strong>{formatProductPrice(product)} <small>/ {product.unit}</small></strong>
                      {!product.active && <span className="inactive-badge">Inactive</span>}
                    </div>
                    {currentUser.role === 'CUSTOMER' && (
                      <div className="product-stock-row">
                        <span className={product.stockQuantity > 0 ? 'stock-available' : 'stock-empty'}>
                          {product.stockQuantity > 0 ? `${product.stockQuantity} in stock` : 'Out of stock'}
                        </span>
                        <button
                          className="secondary-button"
                          type="button"
                          disabled={product.stockQuantity < 1}
                          onClick={() => addToCart(product)}
                        >
                          Add to cart
                        </button>
                      </div>
                    )}
                    {currentUser.role === 'ADMIN' && (
                      <>
                        <div className="product-admin-actions">
                          <button className="text-button" type="button" onClick={() => editProduct(product)}>Edit</button>
                          <button className="text-button" type="button" onClick={() => changeProductStatus(product)}>
                            {product.active ? 'Deactivate' : 'Reactivate'}
                          </button>
                        </div>
                        <form className="stock-form" onSubmit={(event) => updateStock(product, event)}>
                          <label>
                            Stock
                            <input
                              type="number"
                              min="0"
                              max="10000000"
                              required
                              value={stockDrafts[product.id] ?? String(product.stockQuantity)}
                              onChange={(event) => setStockDrafts({ ...stockDrafts, [product.id]: event.target.value })}
                            />
                          </label>
                          <button className="text-button" type="submit">Update stock</button>
                        </form>
                      </>
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}

          <div className="catalog-pagination">
            <button className="secondary-button" type="button" disabled={catalogPage === 0 || catalogLoading} onClick={() => setCatalogPage((page) => page - 1)}>Previous</button>
            <button className="secondary-button" type="button" disabled={catalogPage + 1 >= totalPages || catalogLoading} onClick={() => setCatalogPage((page) => page + 1)}>Next</button>
          </div>
        </section>
      ) : (
        <section className="catalog-teaser" aria-labelledby="catalog-title">
          <div><p className="eyebrow">Coming into view</p><h2 id="catalog-title">Your kitchen's next great order starts here.</h2></div>
          <p>Sign in to browse the product catalog. New restaurant customers can create an account above.</p>
        </section>
      )}

      {currentUser?.role === 'CUSTOMER' && (
        <div className="order-layout">
          <section className="cart-section" aria-labelledby="cart-title">
            <div className="catalog-heading">
              <div>
                <p className="eyebrow">Ready when you are</p>
                <h2 id="cart-title">Your cart</h2>
              </div>
              <span className="catalog-count">{cart.totalQuantity} items</span>
            </div>
            {cartError && <p className="form-message form-message--error" role="alert">{cartError}</p>}
            {cartLoading ? (
              <p className="catalog-empty" role="status">Loading your cart...</p>
            ) : cart.items.length === 0 ? (
              <p className="catalog-empty">Your cart is empty. Add products from the catalog above.</p>
            ) : (
              <>
                <div className="cart-lines">
                  {cart.items.map((item) => (
                    <article className="cart-line" key={item.productId}>
                      <div className="cart-line-copy">
                        <strong>{item.productName}</strong>
                        <span>{item.sku} · {formatAmount(item.unitPrice, item.currencyCode)} / {item.unit}</span>
                        {!item.active && <span className="stock-empty">No longer available</span>}
                        {item.active && item.quantity > item.availableQuantity && (
                          <span className="stock-empty">Only {item.availableQuantity} in stock</span>
                        )}
                      </div>
                      <div className="cart-line-actions">
                        <button type="button" aria-label={`Remove one ${item.productName}`} disabled={item.quantity <= 1} onClick={() => setCartItemQuantity(item.productId, item.quantity - 1)}>−</button>
                        <span>{item.quantity}</span>
                        <button type="button" aria-label={`Add one ${item.productName}`} disabled={!item.active || item.quantity >= item.availableQuantity} onClick={() => setCartItemQuantity(item.productId, item.quantity + 1)}>+</button>
                        <button className="text-button" type="button" onClick={() => removeCartItem(item.productId)}>Remove</button>
                      </div>
                      <strong className="cart-line-total">{formatAmount(item.lineTotal, item.currencyCode)}</strong>
                    </article>
                  ))}
                </div>
                <div className="cart-summary">
                  <span>Subtotal</span>
                  <strong>{formatAmount(cart.subtotal, cart.currencyCode ?? 'LKR')}</strong>
                </div>
                <button className="primary-button" type="button" onClick={checkout} disabled={checkoutLoading || cart.items.some((item) => !item.active || item.quantity > item.availableQuantity)}>
                  {checkoutLoading ? 'Placing order...' : 'Place order'}
                </button>
              </>
            )}
          </section>

          <section className="orders-section" aria-labelledby="orders-title">
            <div className="catalog-heading">
              <div>
                <p className="eyebrow">Your purchases</p>
                <h2 id="orders-title">Order history</h2>
              </div>
            </div>
            {orderError && <p className="form-message form-message--error" role="alert">{orderError}</p>}
            {orders.length === 0 ? (
              <p className="catalog-empty">Your placed orders will appear here.</p>
            ) : (
              <div className="order-list">
                {orders.map((order) => (
                  <article className="order-card" key={order.id}>
                    <div className="order-card-heading">
                      <div><strong>Order {order.id.slice(0, 8)}</strong><span>{new Date(order.placedAt).toLocaleString()}</span></div>
                      <span className={`order-status order-status--${order.status.toLowerCase()}`}>{order.status.toLowerCase()}</span>
                    </div>
                    <ul>{order.items.map((item) => <li key={item.productId}>{item.quantity} × {item.productName} <span>{formatAmount(item.lineTotal, order.currencyCode)}</span></li>)}</ul>
                    <div className="cart-summary"><span>Total</span><strong>{formatAmount(order.totalAmount, order.currencyCode)}</strong></div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      )}

      {currentUser?.role === 'ADMIN' && (
        <section className="orders-section admin-orders-section" aria-labelledby="admin-orders-title">
          <div className="catalog-heading">
            <div><p className="eyebrow">Fulfilment desk</p><h2 id="admin-orders-title">Recent orders</h2></div>
          </div>
          {orderError && <p className="form-message form-message--error" role="alert">{orderError}</p>}
          {orders.length === 0 ? (
            <p className="catalog-empty">Customer orders will appear here.</p>
          ) : (
            <div className="order-list">
              {orders.map((order) => (
                <article className="order-card" key={order.id}>
                  <div className="order-card-heading">
                    <div><strong>Order {order.id.slice(0, 8)}</strong><span>{order.customerEmail} · {new Date(order.placedAt).toLocaleString()}</span></div>
                    <span className={`order-status order-status--${order.status.toLowerCase()}`}>{order.status.toLowerCase()}</span>
                  </div>
                  <ul>{order.items.map((item) => <li key={item.productId}>{item.quantity} × {item.productName} <span>{formatAmount(item.lineTotal, order.currencyCode)}</span></li>)}</ul>
                  <div className="order-admin-actions">
                    <strong>{formatAmount(order.totalAmount, order.currencyCode)}</strong>
                    {order.status === 'PLACED' && <button className="text-button" type="button" onClick={() => updateOrderStatus(order.id, 'PROCESSING')}>Start processing</button>}
                    {order.status === 'PROCESSING' && <button className="text-button" type="button" onClick={() => updateOrderStatus(order.id, 'COMPLETED')}>Mark completed</button>}
                    {(order.status === 'PLACED' || order.status === 'PROCESSING') && <button className="text-button order-cancel" type="button" onClick={() => updateOrderStatus(order.id, 'CANCELLED')}>Cancel and restock</button>}
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      )}

      <section className="foundation-grid" aria-label="Project foundation">
        <article className="foundation-item">
          <span className="item-icon" aria-hidden="true">↗</span>
          <div><h2>One dependable supply run</h2><p>Browse the essentials your team uses every day.</p></div>
        </article>
        <article className="foundation-item">
          <span className="item-icon item-icon--gold" aria-hidden="true">◷</span>
          <div><h2>Built around your service</h2><p>Spend less time sourcing and more time serving.</p></div>
        </article>
        <article className="foundation-item">
          <span className="item-icon item-icon--blue" aria-hidden="true">＋</span>
          <div><h2>Made for growing kitchens</h2><p>A clear, practical workspace for business orders.</p></div>
        </article>
      </section>

      <footer className="page-footer">
        <span>SupplyCart · B2B foodservice ordering</span>
        <span>Phase 4 · Cart, inventory &amp; orders</span>
      </footer>
    </main>
  )
}
