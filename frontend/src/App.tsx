import {BrowserRouter, Link, Route, Routes} from 'react-router-dom'
import {VisitorPage} from './pages/VisitorPage'
import {AdminPage} from './pages/AdminPage'

function App() {
  return (
    <BrowserRouter>
      <nav className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-4xl mx-auto px-4 py-3">
          <div className="flex gap-4">
            <Link to="/" className="text-blue-600 hover:underline font-medium">
              Посетитель
            </Link>
            <Link to="/admin" className="text-blue-600 hover:underline font-medium">
              Владелец
            </Link>
          </div>
        </div>
      </nav>
      <Routes>
        <Route path="/" element={<VisitorPage />} />
        <Route path="/admin" element={<AdminPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
