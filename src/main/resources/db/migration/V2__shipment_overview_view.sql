-- The view the Mandate read broker queries (SPEC §5.1). It grants nothing: kervan-system's
-- seed scripts create the read-only role kervan_reader and give it select on this view.
-- full_name, phone and email are confidential (SPEC §5.2); the broker masks them.
create view v_shipment_overview as
  select s.tracking_no, s.status, s.destination_city, d.delivery_date, d.status as delivery_status,
         d.attempts, c.full_name, c.phone, c.email
  from shipments s join customers c on c.id = s.customer_id left join deliveries d on d.tracking_no = s.tracking_no;
