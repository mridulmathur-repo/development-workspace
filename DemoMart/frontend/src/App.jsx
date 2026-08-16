import { useEffect, useMemo, useState } from 'react'
import { api } from './api.js'

const savedSession = () => {
  try { return JSON.parse(localStorage.getItem('demomart-session')) } catch { return null }
}

const money = value => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(value)
const roleLabel = role => ({ END_USER: 'End user', INVENTORY_MANAGER: 'Inventory manager', ADMIN: 'Administrator' }[role] || role)

function App() {
  const [session, setSession] = useState(savedSession)
  const [view, setView] = useState('catalog')
  const [selectedPhone, setSelectedPhone] = useState(null)
  const [notice, setNotice] = useState('')

  const signIn = next => {
    localStorage.setItem('demomart-session', JSON.stringify(next))
    setSession(next); setView('catalog'); setNotice(`Welcome, ${next.name}`)
  }
  const signOut = () => {
    localStorage.removeItem('demomart-session')
    setSession(null); setSelectedPhone(null); setNotice('')
  }
  if (!session) return <AuthScreen onAuthenticated={signIn} />

  const canManageInventory = ['INVENTORY_MANAGER', 'ADMIN'].includes(session.role)
  const isAdmin = session.role === 'ADMIN'
  const openCheckout = phone => { setSelectedPhone(phone); setView('checkout') }

  return (
    <div className="app-shell">
      <header className="topbar">
        <button className="brand" onClick={() => setView('catalog')} aria-label="DemoMart home">
          <span className="brand-mark">D</span><span>Demo<span>Mart</span></span>
        </button>
        <nav>
          <NavButton active={view === 'catalog'} onClick={() => setView('catalog')}>Phones</NavButton>
          <NavButton active={view === 'orders'} onClick={() => setView('orders')}>My orders</NavButton>
          {canManageInventory && <NavButton active={view === 'inventory'} onClick={() => setView('inventory')}>Add inventory</NavButton>}
          {isAdmin && <NavButton active={view === 'users'} onClick={() => setView('users')}>Manage users</NavButton>}
        </nav>
        <div className="user-menu">
          <div><strong>{session.name}</strong><small>{roleLabel(session.role)}</small></div>
          <button className="quiet-button" onClick={signOut}>Sign out</button>
        </div>
      </header>

      {notice && <div className="notice" onClick={() => setNotice('')}>{notice}</div>}
      <main>
        {view === 'catalog' && <Catalog token={session.token} onBuy={openCheckout} />}
        {view === 'checkout' && selectedPhone && <Checkout token={session.token} phone={selectedPhone} onBack={() => setView('catalog')} onPlaced={() => { setNotice('Order placed — pay when it arrives.'); setView('orders') }} />}
        {view === 'orders' && <Orders token={session.token} />}
        {view === 'inventory' && canManageInventory && <Inventory token={session.token} onAdded={() => { setNotice('Phone added to inventory.'); setView('catalog') }} />}
        {view === 'users' && isAdmin && <UserManagement token={session.token} currentUserId={session.id} />}
      </main>
    </div>
  )
}

function NavButton({ active, children, ...props }) {
  return <button className={active ? 'nav-active' : ''} {...props}>{children}</button>
}

function AuthScreen({ onAuthenticated }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ name: '', email: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const submit = async event => {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const body = mode === 'login' ? { email: form.email, password: form.password } : form
      const result = await api(`/auth/${mode === 'login' ? 'login' : 'register'}`, { method: 'POST', body: JSON.stringify(body) })
      onAuthenticated(result)
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  return (
    <div className="auth-page">
      <section className="auth-story">
        <div className="story-brand"><span className="brand-mark">D</span> DemoMart</div>
        <div className="story-copy">
          <span className="eyebrow">A MICROSERVICE LEARNING STORE</span>
          <h1>Your next phone,<br/><em>without the noise.</em></h1>
          <p>Compare thoughtfully selected phones, order in a few steps, and pay safely when it arrives.</p>
        </div>
        <div className="service-strip"><span>API Gateway</span><span>Eureka discovery</span><span>H2 databases</span></div>
      </section>
      <section className="auth-panel">
        <form className="auth-card" onSubmit={submit}>
          <span className="eyebrow">WELCOME TO DEMOMART</span>
          <h2>{mode === 'login' ? 'Sign in to continue' : 'Create your account'}</h2>
          <p>{mode === 'login' ? 'Access your phones, orders and role-specific tools.' : 'New accounts start with the End user role.'}</p>
          <div className="segmented">
            <button type="button" className={mode === 'login' ? 'selected' : ''} onClick={() => setMode('login')}>Log in</button>
            <button type="button" className={mode === 'register' ? 'selected' : ''} onClick={() => setMode('register')}>Create user</button>
          </div>
          {mode === 'register' && <Field label="Full name"><input required value={form.name} onChange={e => setForm({...form, name: e.target.value})} placeholder="Alex Morgan" /></Field>}
          <Field label="Email"><input required type="email" value={form.email} onChange={e => setForm({...form, email: e.target.value})} placeholder="you@example.com" /></Field>
          <Field label="Password"><input required minLength="8" type="password" value={form.password} onChange={e => setForm({...form, password: e.target.value})} placeholder="At least 8 characters" /></Field>
          {error && <div className="error-box">{error}</div>}
          <button className="primary-button full" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
          <div className="demo-hint"><strong>Demo logins</strong><span>user@demomart.local / User@123</span><span>inventory@demomart.local / Inventory@123</span><span>admin@demomart.local / Admin@123</span></div>
        </form>
      </section>
    </div>
  )
}

function Catalog({ token, onBuy }) {
  const [phones, setPhones] = useState([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const load = async term => {
    setLoading(true); setError('')
    try { setPhones(await api(`/phones?search=${encodeURIComponent(term)}`, {}, token)) }
    catch (e) { setError(e.message) } finally { setLoading(false) }
  }
  useEffect(() => { load('') }, [token])
  return (
    <div className="page catalog-page">
      <div className="page-heading split-heading">
        <div><span className="eyebrow">CURATED MOBILE COLLECTION</span><h1>Find your next phone.</h1><p>Honest choices, clear prices, delivery to your door.</p></div>
        <form className="search" onSubmit={e => { e.preventDefault(); load(search) }}><span>⌕</span><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search brand or model"/><button>Search</button></form>
      </div>
      {error && <div className="error-box">{error}</div>}
      {loading ? <Loading /> : <div className="phone-grid">{phones.map((phone, index) => <PhoneCard key={phone.id} phone={phone} index={index} onBuy={() => onBuy(phone)} />)}</div>}
      {!loading && phones.length === 0 && <Empty title="No phones found" text="Try another brand or model." />}
    </div>
  )
}

function PhoneCard({ phone, index, onBuy }) {
  const palette = ['mint','sand','sky','lilac','coral','slate'][index % 6]
  return (
    <article className="phone-card">
      <div className={`phone-visual ${palette}`}><div className="camera"><i/><i/><i/></div><span>{phone.brand.charAt(0)}</span></div>
      <div className="phone-copy"><span className="phone-brand">{phone.brand}</span><h3>{phone.model}</h3><p>{phone.description}</p><div className="specs"><span>{phone.storage}</span><span>{phone.color}</span></div></div>
      <div className="phone-footer"><div><strong>{money(phone.price)}</strong><small>{phone.stock > 0 ? `${phone.stock} in stock` : 'Out of stock'}</small></div><button className="primary-button" disabled={phone.stock === 0} onClick={onBuy}>Buy now</button></div>
    </article>
  )
}

function Checkout({ token, phone, onBack, onPlaced }) {
  const [address, setAddress] = useState({ addressLine1: '', addressLine2: '', city: '', postalCode: '', country: '' })
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  useEffect(() => { api('/users/me', {}, token).then(user => setAddress({ addressLine1: user.addressLine1 || '', addressLine2: user.addressLine2 || '', city: user.city || '', postalCode: user.postalCode || '', country: user.country || '' })).catch(e => setError(e.message)) }, [token])
  const submit = async event => {
    event.preventDefault(); setBusy(true); setError('')
    try {
      await api('/users/me/address', { method: 'PUT', body: JSON.stringify(address) }, token)
      await api('/orders', { method: 'POST', body: JSON.stringify({ phoneId: phone.id, ...address }) }, token)
      onPlaced()
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  return (
    <div className="page narrow-page"><button className="back-button" onClick={onBack}>← Back to phones</button><div className="page-heading"><span className="eyebrow">SECURE CHECKOUT</span><h1>Delivery details</h1><p>Your address is saved for your next order.</p></div>
      <div className="checkout-grid"><form className="panel form-grid" onSubmit={submit}><Field label="Address line 1"><input required value={address.addressLine1} onChange={e => setAddress({...address,addressLine1:e.target.value})}/></Field><Field label="Address line 2"><input value={address.addressLine2} onChange={e => setAddress({...address,addressLine2:e.target.value})}/></Field><Field label="City"><input required value={address.city} onChange={e => setAddress({...address,city:e.target.value})}/></Field><Field label="Postal code"><input required value={address.postalCode} onChange={e => setAddress({...address,postalCode:e.target.value})}/></Field><Field label="Country"><input required value={address.country} onChange={e => setAddress({...address,country:e.target.value})}/></Field>{error && <div className="error-box span-two">{error}</div>}<button className="primary-button span-two" disabled={busy}>{busy ? 'Placing order…' : 'Place cash-on-delivery order'}</button></form>
        <aside className="panel order-summary"><span className="eyebrow">ORDER SUMMARY</span><h3>{phone.brand} {phone.model}</h3><p>{phone.storage} · {phone.color}</p><div className="summary-line"><span>Phone</span><strong>{money(phone.price)}</strong></div><div className="summary-line"><span>Delivery</span><strong>Free</strong></div><div className="summary-total"><span>Total</span><strong>{money(phone.price)}</strong></div><div className="cod-note">Pay with cash when your order arrives.</div></aside>
      </div>
    </div>
  )
}

function Inventory({ token, onAdded }) {
  const [form, setForm] = useState({ brand:'', model:'', description:'', price:'', stock:'', color:'', storage:'' })
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const submit = async e => { e.preventDefault(); setBusy(true); setError(''); try { await api('/phones', { method:'POST', body:JSON.stringify({...form, price:Number(form.price), stock:Number(form.stock)}) }, token); onAdded() } catch(err) { setError(err.message) } finally { setBusy(false) } }
  return <div className="page narrow-page"><div className="page-heading"><span className="eyebrow">INVENTORY CONTROL</span><h1>Add a phone</h1><p>Create a catalog item and make it immediately available to customers.</p></div><form className="panel form-grid" onSubmit={submit}>{['brand','model','color','storage'].map(key => <Field key={key} label={key[0].toUpperCase()+key.slice(1)}><input required value={form[key]} onChange={e => setForm({...form,[key]:e.target.value})}/></Field>)}<Field label="Price (USD)"><input required type="number" min="0.01" step="0.01" value={form.price} onChange={e => setForm({...form,price:e.target.value})}/></Field><Field label="Starting stock"><input required type="number" min="0" value={form.stock} onChange={e => setForm({...form,stock:e.target.value})}/></Field><Field label="Description" wide><textarea required value={form.description} onChange={e => setForm({...form,description:e.target.value})}/></Field>{error && <div className="error-box span-two">{error}</div>}<button className="primary-button span-two" disabled={busy}>{busy?'Saving…':'Add to inventory'}</button></form></div>
}

function Orders({ token }) {
  const [orders, setOrders] = useState([]); const [error, setError] = useState(''); const [loading, setLoading] = useState(true)
  useEffect(() => { api('/orders/mine', {}, token).then(setOrders).catch(e=>setError(e.message)).finally(()=>setLoading(false)) }, [token])
  return <div className="page narrow-page"><div className="page-heading"><span className="eyebrow">PURCHASE HISTORY</span><h1>My orders</h1><p>Follow every DemoMart purchase in one place.</p></div>{error&&<div className="error-box">{error}</div>}{loading?<Loading/>:orders.length===0?<Empty title="No orders yet" text="Your first phone order will appear here."/>:<div className="order-list">{orders.map(order=><article className="order-row" key={order.id}><div><span className="order-id">ORDER #{order.id}</span><h3>{order.phoneName}</h3><p>{order.addressLine1}, {order.city}, {order.country}</p></div><div><span className="status">{order.status}</span><strong>{money(order.totalAmount)}</strong><small>Cash on delivery</small></div></article>)}</div>}</div>
}

function UserManagement({ token, currentUserId }) {
  const [users, setUsers] = useState([]); const [error,setError]=useState(''); const [loading,setLoading]=useState(true)
  const load=()=>api('/users',{},token).then(setUsers).catch(e=>setError(e.message)).finally(()=>setLoading(false))
  useEffect(load,[token])
  const changeRole=async(id,role)=>{try{await api(`/users/${id}/role`,{method:'PATCH',body:JSON.stringify({role})},token);load()}catch(e){setError(e.message)}}
  return <div className="page"><div className="page-heading"><span className="eyebrow">ADMINISTRATION</span><h1>People and roles</h1><p>Control who can shop, manage inventory, and administer DemoMart.</p></div>{error&&<div className="error-box">{error}</div>}{loading?<Loading/>:<div className="table-panel"><table><thead><tr><th>User</th><th>Email</th><th>Role</th><th>Account</th></tr></thead><tbody>{users.map(user=><tr key={user.id}><td><strong>{user.name}</strong>{user.id===currentUserId&&<small className="you">You</small>}</td><td>{user.email}</td><td><select value={user.role} onChange={e=>changeRole(user.id,e.target.value)}><option value="END_USER">End user</option><option value="INVENTORY_MANAGER">Inventory manager</option><option value="ADMIN">Administrator</option></select></td><td><span className="active-dot">●</span> Active</td></tr>)}</tbody></table></div>}</div>
}

function Field({ label, children, wide }) { return <label className={wide ? 'field span-two' : 'field'}><span>{label}</span>{children}</label> }
function Loading() { return <div className="loading"><i/><span>Loading DemoMart…</span></div> }
function Empty({title,text}) { return <div className="empty"><div>◌</div><h3>{title}</h3><p>{text}</p></div> }

export default App
