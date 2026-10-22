# Inventory Management

This module provides a complete inventory management system for products using **SWR + GraphQL-request** for data fetching and state management.

## 🚀 Features

- **Product Grid View**: Display products in a responsive card layout
- **CRUD Operations**: Add, Edit, Delete products with form validation
- **Search & Pagination**: Search products and paginate through results
- **Optimistic Updates**: Immediate UI updates for better UX
- **Loading States**: Skeleton screens and loading indicators
- **Error Handling**: Comprehensive error handling with toast notifications

## 📁 Project Structure

```
/src/lib/inventory/
├── types.ts          # TypeScript interfaces and types
├── gql.ts            # GraphQL client configuration
├── queries.ts        # GraphQL queries and mutations
├── hooks.ts          # SWR hooks with optimistic updates
└── index.ts          # Library exports

/src/screens/products/inventory/
├── InventoryCard.tsx         # Product card component
├── InventoryFormModal.tsx    # Add/Edit product modal
├── ConfirmDialog.tsx         # Delete confirmation dialog
├── GridEmpty.tsx             # Empty state component
├── GridSkeleton.tsx          # Loading skeleton component
├── InventoryManagePage.tsx   # Main page component
└── index.ts                  # Component exports

/pages/products/inventory/manage/
└── index.tsx         # Next.js page route
```

## 🛠 Dependencies

- **swr**: Data fetching and caching
- **graphql**: GraphQL query language
- **graphql-request**: Lightweight GraphQL client
- **react-hook-form**: Form state management and validation
- **@chakra-ui/react**: UI components

## 🔧 Setup

1. **Environment Variable**: Ensure `NEXT_PUBLIC_INVENTORY_GRAPHQL_API_URL` is set in your environment
2. **GraphQL API**: The API should implement the following schema:

```graphql
type Product {
  _id: String!
  name: String!
  description: String
  price: Float
  salePrice: Float
  quantity: Int
  image: String
  published: Boolean
  deleted: Boolean
  createdAt: String
  updatedAt: String
  totalCount: Int
}

input paginationInput {
  page: Int!
  limit: Int!
  search: String
}

input productInput {
  name: String!
  description: String
  price: Float
  salePrice: Float
  quantity: Int
  image: String
  published: Boolean
}

input productUpdateInput {
  _id: String!
  name: String
  description: String
  price: Float
  salePrice: Float
  quantity: Int
  image: String
  published: Boolean
}

type Query {
  products(paginationInput: paginationInput!): [Product!]!
  product(productId: String!): Product
}

type Mutation {
  addProduct(productInput: productInput!): Product!
  updateProduct(productUpdateInput: productUpdateInput!): Product!
  deleteProduct(productId: String!): Product!
}
```

## 🎯 Usage

Navigate to `/products/inventory/manage` to access the inventory management interface.

### Key Features:

1. **Product Cards**: Each product is displayed in a card with image, name, description, price, and action buttons
2. **Add Product**: Click the "Add Product" button to open the form modal
3. **Edit Product**: Click "Edit" on any product card to modify it
4. **Delete Product**: Click "Delete" to remove a product (with confirmation)
5. **Search**: Use the search bar to filter products by name or description
6. **Pagination**: Navigate through multiple pages of products

## 🔄 Data Flow

1. **SWR Hooks**: Handle data fetching with automatic caching and revalidation
2. **Optimistic Updates**: UI updates immediately while API calls run in background
3. **Error Handling**: Failed operations show error messages and revert optimistic updates
4. **Cache Management**: Manual cache invalidation after successful mutations

## 🎨 UI Components

- **Responsive Design**: Works on desktop, tablet, and mobile
- **Loading States**: Skeleton screens during data loading
- **Empty States**: Friendly UI when no products exist
- **Form Validation**: Real-time validation with error messages
- **Toast Notifications**: Success and error feedback

## 🚦 API Integration

The module uses GraphQL with the following operations:

- `products()` - Fetch paginated product list
- `product()` - Fetch single product details
- `addProduct()` - Create new product
- `updateProduct()` - Update existing product
- `deleteProduct()` - Delete product

All mutations include optimistic updates for immediate UI feedback.
